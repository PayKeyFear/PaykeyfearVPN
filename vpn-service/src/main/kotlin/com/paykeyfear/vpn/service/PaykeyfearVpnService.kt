package com.paykeyfear.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.paykeyfear.vpn.core.Protector
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.core.model.TunnelStats
import com.paykeyfear.vpn.geo.GeoCidr
import com.paykeyfear.vpn.geo.RouteExclusion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * Android foreground VpnService entry point. Owns the tun descriptor and
 * delegates protocol handling to a Hilt-injected [TunnelController] singleton
 * shared with the rest of the app (so UI and tunnel observe the same state).
 */
@AndroidEntryPoint
class PaykeyfearVpnService : VpnService() {
    @Inject lateinit var controller: TunnelController

    @Inject lateinit var settings: TunnelSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelJob: Job? = null

    // We don't keep the ParcelFileDescriptor — once Builder.establish()
    // returns we immediately call detachFd() so the JVM-side wrapper
    // releases ownership of the tun fd, then close the (now-empty) PFD.
    // The native backend (Go) becomes the sole owner of the fd and is
    // responsible for closing it on Stop. Keeping a PFD alive in parallel
    // with Go ownership causes binder-thread fdsan SIGABRTs when the OS
    // recycles the fd number.

    @Volatile
    private var lastConfig: ConnectionConfig? = null

    private val networkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            // Android emits an `onAvailable` whenever an underlying network
            // appears (Wi-Fi ⇄ cellular, suspend/resume, hotspot toggles…).
            // On every transition we tear down the current tunnel and
            // re-establish with the same config — same pattern WireGuard's
            // official client uses.
            //
            // Skipped on the very first callback (which fires immediately
            // after registration with the current network) so we don't
            // bounce the tunnel that's only just being established. Also
            // skipped for VPN-transport networks — our own tunnel shows up
            // here once Builder.establish() succeeds, and reacting to it
            // would cancel the in-flight launchTunnel coroutine.
            private var first = true

            override fun onAvailable(network: Network) {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val caps = cm?.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                    return
                }
                // Tell the framework which network the VPN's own outbound
                // sockets should use, so AWG/VLESS/Hysteria2 traffic
                // bypasses our own tun (otherwise it loops and no data
                // flows). amneziawg-go's default Bind does not call
                // VpnService.protect, so this is the only thing keeping
                // AWG packets off the tunnel.
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
                if (first) {
                    first = false
                    return
                }
                val cfg = lastConfig ?: return
                Timber.tag(TAG).i("Network changed (%s) — reconnecting", network)
                launchTunnel(cfg)
            }

            override fun onLost(network: Network) {
                runCatching { setUnderlyingNetworks(null) }
            }
        }
    }

    private var networkCallbackRegistered = false

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        // Refresh the persistent notification whenever connection state or
        // throughput changes — gives users live "↓ X kB/s ↑ Y kB/s" feedback
        // without requiring the app to be in the foreground.
        controller.state
            .combine(controller.stats) { state, stats -> state to stats }
            .distinctUntilChanged()
            .onEach { (state, stats) -> updateNotification(state, stats) }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG) ?: return START_NOT_STICKY
                val config = Json.decodeFromString<ConnectionConfig>(configJson)
                launchTunnel(config)
            }
            ACTION_STOP -> stopTunnel()
            // Null action or unknown action: the system (Always-on VPN) is
            // starting us without explicit intent data. Resolve the last
            // selected server and bring the tunnel up with it.
            null -> launchSelected()
        }
        return START_STICKY
    }

    private fun launchSelected() {
        scope.launch {
            val config = runCatching { settings.selectedConfig() }.getOrNull()
            if (config == null) {
                Timber.w("Always-on start requested but no server is selected")
                stopSelf()
                return@launch
            }
            launchTunnel(config)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == SERVICE_INTERFACE) return super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        scope.cancel()
        super.onDestroy()
    }

    private fun launchTunnel(config: ConnectionConfig) {
        lastConfig = config
        registerNetworkCallback()
        val priorJob = tunnelJob
        tunnelJob = scope.launch {
            runCatching { priorJob?.cancelAndJoin() }
            runCatching { controller.stop() }

            val split = runCatching { settings.splitTunnel() }.getOrDefault(SplitTunnelConfig.OFF)
            val ruBypass = runCatching { settings.ruBypass() }.getOrDefault(RuBypassConfig.OFF)
            val builder = Builder()
                .setSession(config.displayName)
                // RU bypass adds throw routes to the TUN. Cronet (YouTube)
                // sees the resulting network as "split routing" and
                // refuses to do PMTU discovery — AWG headers fragment
                // QUIC packets and Onesie hangs with InterruptedIOException.
                // Drop MTU to the IPv6 minimum (1280) when bypass is on
                // so QUIC's initial packets always fit; idle without bypass
                // keeps the AWG-config MTU.
                .setMtu(if (ruBypass.enabled) BYPASS_MTU else configMtu(config))
            applyRoutes(builder, ruBypass)
            applyAddresses(builder, config)
            applyDns(builder, config, ruBypass)
            applySplitTunnel(builder, split)
            val pfd = builder.establish() ?: run {
                Timber.e("VpnService.Builder.establish() returned null")
                stopSelf()
                return@launch
            }
            // detachFd() releases the JVM wrapper's ownership of the fd;
            // we then immediately close the (now empty) PFD. From this
            // point on, the fd is owned exclusively by the native backend,
            // which closes it in both success (Stop) and failure (Start
            // error path) paths. We must NOT close it from Kotlin — the
            // fd number may already be recycled, and a double-close trips
            // fdsan with SIGABRT on the binder thread.
            val nativeFd = pfd.detachFd()
            runCatching { pfd.close() }
            runCatching { controller.start(config, nativeFd, protector()) }.onFailure {
                Timber.e(it, "Tunnel failed")
                stopSelf()
            }
        }
    }

    private fun protector(): Protector = Protector { fd -> protect(fd) }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching { cm.registerNetworkCallback(req, networkCallback) }
            .onSuccess { networkCallbackRegistered = true }
            .onFailure { Timber.tag(TAG).w(it, "Failed to register network callback") }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
    }

    private fun applyRoutes(builder: Builder, ruBypass: RuBypassConfig) {
        if (!ruBypass.enabled || (ruBypass.ipv4.isEmpty() && ruBypass.ipv6.isEmpty())) {
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("::", 0)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applyRoutesWithExcludes(builder, ruBypass)
        } else {
            applyRoutesWithComplement(builder, ruBypass)
        }
    }

    /** API 33+: keep `0.0.0.0/0` default + call `excludeRoute` for every RU prefix. */
    private fun applyRoutesWithExcludes(builder: Builder, ruBypass: RuBypassConfig) {
        builder.addRoute("0.0.0.0", 0)
        builder.addRoute("::", 0)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        // v2fly's raw RU list is ~21k entries. Binder establishVpn caps at
        // ~1 MB and downstream NetworkMonitorManager broadcasts the route
        // list to its observers — those broadcasts blow up much earlier
        // (~650 KB) and after a few failures Android tears the VPN down.
        // Empirically a hard cap below ~600 routes keeps the broadcasts
        // alive. We progressively coarsen v4 prefixes (/16 → /12 → /10)
        // until the v4 entry count falls under [MAX_EXCLUDE_ENTRIES_V4],
        // and skip v6 for the same reason — mobile carriers are usually
        // v4 anyway and v6 dual-stack would push us back over the cap.
        val v4 = aggregateUnderCap(ruBypass.ipv4, MAX_EXCLUDE_ENTRIES_V4)
        var ok = 0
        var failed = 0
        v4.forEach { cidr ->
            runCatching {
                builder.excludeRoute(android.net.IpPrefix(java.net.InetAddress.getByName(cidr.address), cidr.prefixLength))
            }.onSuccess { ok++ }.onFailure { failed++ }
        }
        Timber.tag(TAG).i(
            "RU bypass: excludeRoute applied=%d failed=%d v4=%d v6=skipped (from raw v4=%d v6=%d)",
            ok,
            failed,
            v4.size,
            ruBypass.ipv4.size,
            ruBypass.ipv6.size,
        )
    }

    private fun aggregateUnderCap(cidrs: List<GeoCidr>, maxEntries: Int): List<GeoCidr> {
        for (prefix in COARSEN_LADDER_V4) {
            val coarse = RouteExclusion.coarsenIpv4(cidrs, prefix)
            // Strip well-known global CDN ranges (Google, Cloudflare,
            // Akamai etc.) that coarsening picks up as collateral. RU users
            // bypassing these direct hit RKN's TCP block on googlevideo /
            // similar — keeps YouTube and friends going through the tunnel.
            val withoutGlobal = RouteExclusion.subtractIpv4(coarse, GLOBAL_CDN_KEEP_TUNNELED)
            val agg = RouteExclusion.aggregateIpv4(withoutGlobal)
            if (agg.size <= maxEntries) {
                Timber.tag(TAG).d("RU bypass: coarsen /%d → %d entries (cap %d)", prefix, agg.size, maxEntries)
                return agg
            }
        }
        // Final fallback: coarsest /8 — should always fit but very lossy.
        val coarse = RouteExclusion.coarsenIpv4(cidrs, 8)
        return RouteExclusion.aggregateIpv4(RouteExclusion.subtractIpv4(coarse, GLOBAL_CDN_KEEP_TUNNELED))
    }

    /** API < 33: Builder has no excludeRoute — use the precomputed complement as addRoute list. */
    private fun applyRoutesWithComplement(builder: Builder, ruBypass: RuBypassConfig) {
        val v4Coarse = RouteExclusion.aggregateIpv4(RouteExclusion.coarsenIpv4(ruBypass.ipv4, COARSEN_V4))
        val v6Coarse = RouteExclusion.aggregateIpv6(RouteExclusion.coarsenIpv6(ruBypass.ipv6, COARSEN_V6))
        val v4Complement = RouteExclusion.complementIpv4(v4Coarse)
        val v6Complement = RouteExclusion.complementIpv6(v6Coarse)
        var ok = 0
        var failed = 0
        (v4Complement + v6Complement).forEach { cidr ->
            runCatching { builder.addRoute(cidr.address, cidr.prefixLength) }
                .onSuccess { ok++ }
                .onFailure { failed++ }
        }
        Timber.tag(TAG).i("RU bypass: addRoute complement applied=%d failed=%d (SDK<33)", ok, failed)
    }

    private fun applySplitTunnel(builder: Builder, split: SplitTunnelConfig) {
        if (split.mode == SplitTunnelMode.Off || split.packages.isEmpty()) return
        // Never include our own package — otherwise we'd route the VPN client
        // through its own tunnel, causing a startup loop.
        val filtered = split.packages - packageName
        filtered.forEach { pkg ->
            runCatching {
                when (split.mode) {
                    SplitTunnelMode.Allowlist -> builder.addAllowedApplication(pkg)
                    SplitTunnelMode.Denylist -> builder.addDisallowedApplication(pkg)
                    SplitTunnelMode.Off -> Unit
                }
            }.onFailure { err ->
                if (err is PackageManager.NameNotFoundException) {
                    Timber.w("Split-tunnel package %s not installed; skipping", pkg)
                } else {
                    Timber.w(err, "Failed to apply split rule for %s", pkg)
                }
            }
        }
    }

    private fun stopTunnel() {
        lastConfig = null
        unregisterNetworkCallback()
        val prior = tunnelJob
        scope.launch {
            runCatching { prior?.cancelAndJoin() }
            runCatching { controller.stop() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun configMtu(config: ConnectionConfig): Int =
        when (config) {
            is ConnectionConfig.Awg -> config.mtu ?: DEFAULT_MTU
            is ConnectionConfig.Vless -> VLESS_MTU
            is ConnectionConfig.Hysteria2 -> HYSTERIA2_MTU
        }

    private fun applyAddresses(builder: Builder, config: ConnectionConfig) {
        val cidrs = when (config) {
            is ConnectionConfig.Awg -> config.addresses
            // VLESS and Hysteria2 are tun2socks/QUIC overlays — the inner
            // tun has no real "client address" assigned by the server, so
            // we synthesize a pair of unique-local IPs from a stable
            // private range. v4 + v6 keeps dual-stack apps happy.
            is ConnectionConfig.Vless,
            is ConnectionConfig.Hysteria2,
            -> SYNTHETIC_OVERLAY_ADDRESSES
        }
        cidrs.forEach { cidr ->
            val (addr, prefix) = cidr.split("/", limit = 2)
                .let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 32) }
            builder.addAddress(addr, prefix)
        }
    }

    private fun applyDns(builder: Builder, config: ConnectionConfig, @Suppress("UNUSED_PARAMETER") ruBypass: RuBypassConfig) {
        // RU bypass is IP-based (excludeRoute), not DNS-based. Earlier we
        // also front-loaded Yandex DNS (77.88.8.8) hoping it would return
        // RU-local CDN IPs for global services — but that hurt YouTube and
        // similar services badly: Yandex sees the user's real RU IP and
        // returns the RU-blocked Google CDN, those IPs sit inside our RU
        // bypass list (v2fly geoip:google ⊂ ru), so YouTube traffic exits
        // direct from RU and the upstream blocks it. Authoritative records
        // for genuine RU sites (ya.ru, vk.com, mail.ru) point at RU /16s
        // regardless of which resolver asks, so the IP-level bypass keeps
        // working without the DNS hack.
        defaultDnsFor(config).forEach(builder::addDnsServer)
    }

    private fun defaultDnsFor(config: ConnectionConfig): List<String> =
        when (config) {
            // AmneziaVPN AWG bundles often include AmneziaDNS (172.29.x.x)
            // as the first resolver. AmneziaDNS returns NXDOMAIN for
            // YouTube's per-session CDN hostnames (r1---sn-XXX.googlevideo.com),
            // and Android's system resolver does NOT fall back to the next
            // server on NXDOMAIN — so video playback stalls. Push private
            // resolvers (10/172.16-31/192.168) to the back of the list so a
            // public DNS is tried first.
            is ConnectionConfig.Awg -> reorderPrivateDnsLast(config.dns).ifEmpty { DEFAULT_DNS }
            is ConnectionConfig.Vless,
            is ConnectionConfig.Hysteria2,
            -> DEFAULT_DNS
        }

    private fun reorderPrivateDnsLast(dns: List<String>): List<String> {
        if (dns.isEmpty()) return dns
        val (public_, private_) = dns.partition { !isPrivateIpv4(it) }
        return public_ + private_
    }

    private fun isPrivateIpv4(addr: String): Boolean {
        val parts = addr.split('.')
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        return when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            else -> false
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL_ID, "VPN Tunnel", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Active VPN tunnel"
                    setShowBadge(false)
                }
            nm.createNotificationChannel(channel)
        }
        val notification = buildNotification("PaykeyfearVPN", "Tunnel is starting")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: TunnelState, stats: TunnelStats) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = when (state) {
            TunnelState.Disconnected, TunnelState.Disconnecting -> "PaykeyfearVPN"
            TunnelState.Connecting -> "Connecting…"
            is TunnelState.Connected -> "Connected — ${state.protocol.displayName}"
            is TunnelState.Error -> "VPN error"
        }
        val text = when (state) {
            is TunnelState.Connected -> formatStats(stats)
            is TunnelState.Error -> state.message
            else -> "Tunnel is ${state.javaClass.simpleName.lowercase()}"
        }
        val showStopAction = state is TunnelState.Connected || state == TunnelState.Connecting
        nm.notify(NOTIFICATION_ID, buildNotification(title, text, showStopAction))
    }

    private fun buildNotification(title: String, text: String, showStopAction: Boolean = false): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
        if (showStopAction) {
            val stopPendingIntent = PendingIntent.getService(
                this,
                1,
                buildStopIntent(this),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPendingIntent)
        }
        return builder.build()
    }

    private fun formatStats(stats: TunnelStats): String {
        return "↓ ${formatBytes(stats.rxBytes)}   ↑ ${formatBytes(stats.txBytes)}"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1_000) return "$bytes B"
        val units = arrayOf("kB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1_000
        var idx = 0
        while (value >= 1_000 && idx < units.lastIndex) {
            value /= 1_000
            idx++
        }
        return "%.1f %s".format(value, units[idx])
    }

    companion object {
        const val ACTION_START = "com.paykeyfear.vpn.service.START"
        const val ACTION_STOP = "com.paykeyfear.vpn.service.STOP"
        const val EXTRA_CONFIG = "config_json"
        private const val TAG = "PaykeyfearVpnService"
        private const val CHANNEL_ID = "paykeyfear_vpn_tunnel"
        private const val NOTIFICATION_ID = 0x50_56_4E_01
        private const val DEFAULT_MTU = 1420

        // IPv6 minimum MTU (RFC 8200). Used when RU bypass is on to make
        // QUIC packets tolerant of any extra encapsulation along the path.
        private const val BYPASS_MTU = 1280

        // Aggressive RU-bypass coarsening to fit under the Binder 1 MB
        // establishVpn parcel cap. /16 + /32 empirically drops 21 448 v4
        // entries to ~300 and 9 000 v6 entries to ~200.
        private const val COARSEN_V4 = 16
        private const val COARSEN_V6 = 32

        // Hard cap on excludeRoute entries we feed to VpnService.Builder.
        // The Binder establishVpn parcel itself can hold up to ~1 MB but
        // downstream NetworkMonitorManager broadcasts the same route list
        // and dies at ~650 KB (~150 B per IpPrefix in the parcel). Skipping
        // v6 entirely (mobile is v4) keeps the v4 /16 list (~1.4 k entries
        // = ~210 KB) well under the cap, so we don't have to coarsen further.
        // A 500-entry cap forced /10–/12 coarsening which over-covered
        // Google's CDN ranges and broke YouTube — leave headroom instead.
        private const val MAX_EXCLUDE_ENTRIES_V4 = 2000

        // Progressive coarsening ladder for the v4 RU CIDR set. Tried in
        // order; first prefix whose aggregated size fits MAX_EXCLUDE_ENTRIES_V4
        // wins. /16 is the floor — anything coarser starts catching huge
        // non-RU CDN ranges (Google, Cloudflare) and bypass turns into a
        // YouTube-killer.
        private val COARSEN_LADDER_V4 = intArrayOf(16, 15, 14)

        // Global CDN ranges that must NEVER bypass the tunnel — even if
        // they happen to fall inside a coarsened RU /16 (74.124.x is RU,
        // 74.125.x is Google's videoplayback CDN, both share /15). RU
        // users routing these direct hit RKN's TCP block on googlevideo /
        // googleapis and YouTube/ads/Onesie hang. Sourced from the public
        // gstatic.com/ipranges/goog.json (Google AS15169) plus
        // 1.1.1.0/24, 1.0.0.0/24 (Cloudflare DNS) for the same reason.
        private val GLOBAL_CDN_KEEP_TUNNELED: List<GeoCidr> = listOf(
            // Cloudflare 1.1.1.1 / 1.0.0.1
            GeoCidr("1.0.0.0", 24, false),
            GeoCidr("1.1.1.0", 24, false),
            // Google AS15169 — keep blocks that overlap with v2fly RU /16s.
            GeoCidr("8.8.4.0", 24, false),
            GeoCidr("8.8.8.0", 24, false),
            GeoCidr("8.34.208.0", 20, false),
            GeoCidr("8.35.192.0", 20, false),
            GeoCidr("23.236.48.0", 20, false),
            GeoCidr("23.251.128.0", 19, false),
            GeoCidr("34.0.0.0", 9, false),
            GeoCidr("34.128.0.0", 10, false),
            GeoCidr("35.184.0.0", 13, false),
            GeoCidr("35.192.0.0", 14, false),
            GeoCidr("35.196.0.0", 15, false),
            GeoCidr("35.198.0.0", 16, false),
            GeoCidr("35.199.0.0", 17, false),
            GeoCidr("35.199.128.0", 18, false),
            GeoCidr("35.200.0.0", 13, false),
            GeoCidr("35.208.0.0", 12, false),
            GeoCidr("35.224.0.0", 12, false),
            GeoCidr("35.240.0.0", 13, false),
            GeoCidr("64.15.112.0", 20, false),
            GeoCidr("64.233.160.0", 19, false),
            GeoCidr("66.22.228.0", 23, false),
            GeoCidr("66.102.0.0", 20, false),
            GeoCidr("66.249.64.0", 19, false),
            GeoCidr("70.32.128.0", 19, false),
            GeoCidr("72.14.192.0", 18, false),
            GeoCidr("74.114.24.0", 21, false),
            GeoCidr("74.125.0.0", 16, false),
            GeoCidr("104.154.0.0", 15, false),
            GeoCidr("104.196.0.0", 14, false),
            GeoCidr("104.237.160.0", 19, false),
            GeoCidr("107.167.160.0", 19, false),
            GeoCidr("107.178.192.0", 18, false),
            GeoCidr("108.59.80.0", 20, false),
            GeoCidr("108.170.192.0", 18, false),
            GeoCidr("108.177.0.0", 17, false),
            GeoCidr("130.211.0.0", 16, false),
            GeoCidr("136.112.0.0", 12, false),
            GeoCidr("142.250.0.0", 15, false),
            GeoCidr("146.148.0.0", 17, false),
            GeoCidr("162.216.148.0", 22, false),
            GeoCidr("162.222.176.0", 21, false),
            GeoCidr("172.110.32.0", 21, false),
            GeoCidr("172.217.0.0", 16, false),
            GeoCidr("172.253.0.0", 16, false),
            GeoCidr("173.194.0.0", 16, false),
            GeoCidr("173.255.112.0", 20, false),
            GeoCidr("192.158.28.0", 22, false),
            GeoCidr("192.178.0.0", 15, false),
            GeoCidr("193.186.4.0", 24, false),
            GeoCidr("199.36.154.0", 23, false),
            GeoCidr("199.36.156.0", 24, false),
            GeoCidr("199.192.112.0", 22, false),
            GeoCidr("199.223.232.0", 21, false),
            GeoCidr("207.223.160.0", 20, false),
            GeoCidr("208.65.152.0", 22, false),
            GeoCidr("208.68.108.0", 22, false),
            GeoCidr("208.81.188.0", 22, false),
            GeoCidr("208.117.224.0", 19, false),
            GeoCidr("209.85.128.0", 17, false),
            GeoCidr("216.58.192.0", 19, false),
            GeoCidr("216.73.80.0", 20, false),
            GeoCidr("216.239.32.0", 19, false),
            // Telegram AS62041 / AS59930 — RKN throttles Telegram TCP
            // from RU IPs since 2024, so messenger reconnects fail when
            // these ranges happen to fall inside coarsened bypass blocks.
            GeoCidr("91.105.192.0", 23, false),
            GeoCidr("91.108.4.0", 22, false),
            GeoCidr("91.108.8.0", 21, false),
            GeoCidr("91.108.12.0", 22, false),
            GeoCidr("91.108.16.0", 21, false),
            GeoCidr("91.108.20.0", 22, false),
            GeoCidr("91.108.56.0", 22, false),
            GeoCidr("95.161.64.0", 20, false),
            GeoCidr("149.154.160.0", 20, false),
            GeoCidr("185.76.151.0", 24, false),
            // Meta / Facebook (Instagram, WhatsApp) — same story.
            GeoCidr("31.13.24.0", 21, false),
            GeoCidr("31.13.64.0", 18, false),
            GeoCidr("66.220.144.0", 20, false),
            GeoCidr("69.63.176.0", 20, false),
            GeoCidr("69.171.224.0", 19, false),
            GeoCidr("74.119.76.0", 22, false),
            GeoCidr("102.132.96.0", 20, false),
            GeoCidr("103.4.96.0", 22, false),
            GeoCidr("129.134.0.0", 16, false),
            GeoCidr("147.75.208.0", 20, false),
            GeoCidr("157.240.0.0", 16, false),
            GeoCidr("169.150.224.0", 19, false),
            GeoCidr("169.230.0.0", 16, false),
            GeoCidr("173.252.64.0", 18, false),
            GeoCidr("179.60.192.0", 22, false),
            GeoCidr("185.60.216.0", 22, false),
            GeoCidr("185.89.216.0", 22, false),
            GeoCidr("199.201.64.0", 22, false),
            GeoCidr("204.15.20.0", 22, false),
            // Cloudflare CDN (1.1.1.0/24 and 1.0.0.0/24 already above).
            GeoCidr("104.16.0.0", 13, false),
            GeoCidr("104.24.0.0", 14, false),
            GeoCidr("172.64.0.0", 13, false),
            GeoCidr("131.0.72.0", 22, false),
            GeoCidr("141.101.64.0", 18, false),
            GeoCidr("162.158.0.0", 15, false),
            GeoCidr("173.245.48.0", 20, false),
            GeoCidr("188.114.96.0", 20, false),
            GeoCidr("190.93.240.0", 20, false),
            GeoCidr("197.234.240.0", 22, false),
            GeoCidr("198.41.128.0", 17, false),
            // Twitter / X (AS13414) — blocked in RU.
            GeoCidr("104.244.40.0", 21, false),
            GeoCidr("192.133.76.0", 22, false),
            GeoCidr("199.16.156.0", 22, false),
            GeoCidr("199.59.148.0", 22, false),
            // LinkedIn (AS14413) — blocked in RU.
            GeoCidr("108.174.0.0", 20, false),
            GeoCidr("144.2.0.0", 16, false),
            // Discord (uses own AS49544 + Cloudflare) — blocked in RU since 2024.
            GeoCidr("162.159.128.0", 17, false),
            // Apple (AS714) — owns the entirety of 17/8 so keep that block
            // tunneled wholesale; AppStore/iCloud/APNs run on subranges of it.
            GeoCidr("17.0.0.0", 8, false),
            // Microsoft (AS8075) — Outlook / Office / Skype / Teams. Pick the
            // service-relevant /14-/16 blocks; not the whole 20/8 Azure range
            // (Azure hosts plenty of RU-bound origins too).
            GeoCidr("13.64.0.0", 11, false),
            GeoCidr("13.96.0.0", 13, false),
            GeoCidr("23.96.0.0", 13, false),
            GeoCidr("40.64.0.0", 10, false),
            GeoCidr("52.96.0.0", 12, false),
            GeoCidr("65.52.0.0", 14, false),
            GeoCidr("104.40.0.0", 13, false),
            GeoCidr("131.107.0.0", 16, false),
            GeoCidr("132.245.0.0", 16, false),
            GeoCidr("134.170.0.0", 16, false),
            GeoCidr("137.135.0.0", 16, false),
            GeoCidr("138.91.0.0", 16, false),
            GeoCidr("157.55.0.0", 16, false),
            GeoCidr("168.61.0.0", 16, false),
            GeoCidr("168.62.0.0", 15, false),
            GeoCidr("191.232.0.0", 13, false),
            GeoCidr("207.46.0.0", 16, false),
            // GitHub (AS36459).
            GeoCidr("140.82.112.0", 20, false),
            GeoCidr("143.55.64.0", 20, false),
            GeoCidr("185.199.108.0", 22, false),
            GeoCidr("192.30.252.0", 22, false),
            // Spotify (AS8403) own ranges (Fastly 151.101/16 left out — too
            // broad, hosts many RU sites too).
            GeoCidr("35.186.224.0", 20, false),
            GeoCidr("78.31.8.0", 21, false),
            GeoCidr("78.31.16.0", 21, false),
        )

        // tun2socks engine MTU above; matches what we ask Go for.
        private const val VLESS_MTU = 1500

        // QUIC overhead is ~70 bytes; sized so the inner IP layer fits in a
        // single QUIC packet on the standard internet 1500-byte path MTU.
        private const val HYSTERIA2_MTU = 1400

        private val DEFAULT_DNS = listOf("1.1.1.1", "8.8.8.8")

        // Standard AmneziaVPN AmneziaDNS docker bridge IP. Always reachable
        // through the AWG tunnel when the operator enabled the AmneziaDNS
        // container — falls through silently if not.
        private const val AMNEZIA_DNS = "172.29.172.254"

        private val SYNTHETIC_OVERLAY_ADDRESSES = listOf(
            "172.19.0.2/32",
            "fdfe:dcba:9876::1/128",
        )

        fun buildStartIntent(context: Context, config: ConnectionConfig): Intent =
            Intent(context, PaykeyfearVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG, Json.encodeToString(ConnectionConfig.serializer(), config))
            }

        fun buildStopIntent(context: Context): Intent =
            Intent(context, PaykeyfearVpnService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
