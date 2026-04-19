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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber

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

    @Volatile
    private var lastConfig: ConnectionConfig? = null

    private val networkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            // Android emits an `onAvailable` whenever the active default
            // network changes (Wi-Fi ⇄ cellular, suspend/resume, hotspot
            // toggles…). On every transition we tear down the current
            // tunnel and re-establish with the same config — same pattern
            // WireGuard's official client uses.
            //
            // Skipped on the very first callback (which fires immediately
            // after registration with the current network) so we don't
            // bounce the tunnel that's only just being established.
            private var first = true

            override fun onAvailable(network: Network) {
                if (first) {
                    first = false
                    return
                }
                val cfg = lastConfig ?: return
                Timber.tag(TAG).i("Network changed (%s) — reconnecting", network)
                launchTunnel(cfg)
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
        tunnelJob?.cancel()
        tunnelJob = scope.launch {
            // Stop any previous tunnel cleanly before rebuilding the tun fd.
            // launchTunnel may be re-entered from the network callback
            // while the controller is still in Connected state.
            runCatching { controller.stop() }
            val split = runCatching { settings.splitTunnel() }.getOrDefault(SplitTunnelConfig.OFF)
            val builder = Builder()
                .setSession(config.displayName)
                .setMtu(configMtu(config))
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
            applyAddresses(builder, config)
            applyDns(builder, config)
            applySplitTunnel(builder, split)
            val pfd = builder.establish() ?: run {
                Timber.e("VpnService.Builder.establish() returned null")
                stopSelf()
                return@launch
            }
            val protector = Protector { fd -> protect(fd) }
            runCatching { controller.start(config, pfd.detachFd(), protector) }.onFailure {
                Timber.e(it, "Tunnel failed")
                stopSelf()
            }
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
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
        scope.launch {
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

    private fun applyDns(builder: Builder, config: ConnectionConfig) {
        val dns = when (config) {
            is ConnectionConfig.Awg -> config.dns.ifEmpty { DEFAULT_DNS }
            is ConnectionConfig.Vless,
            is ConnectionConfig.Hysteria2,
            -> DEFAULT_DNS
        }
        dns.forEach(builder::addDnsServer)
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
        nm.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
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

        // tun2socks engine MTU above; matches what we ask Go for.
        private const val VLESS_MTU = 1500

        // QUIC overhead is ~70 bytes; sized so the inner IP layer fits in a
        // single QUIC packet on the standard internet 1500-byte path MTU.
        private const val HYSTERIA2_MTU = 1400

        private val DEFAULT_DNS = listOf("1.1.1.1", "8.8.8.8")

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
