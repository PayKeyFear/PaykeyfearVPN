package com.paykeyfear.vpn.protocols.vless

import com.paykeyfear.vpn.core.Protector
import com.paykeyfear.vpn.core.VpnTunnel
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.core.model.TunnelStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * VLESS tunnel backed by Xray-core and a userspace tun2socks bridge,
 * both shipped together as `vless.aar` from `third_party/vless-mobile/`.
 *
 * Flow:
 *   1. [XrayAdapter.startXray] boots Xray-core with a SOCKS5 inbound on
 *      [socksPort]. Xray uses [Protector] on every outbound socket it opens.
 *   2. [Tun2SocksBridge.start] pumps packets between the caller's [tunFd] and
 *      `127.0.0.1:[socksPort]`, translating L3 → SOCKS5 on the fly.
 *
 * Both adapters default to no-op implementations so unit tests and fresh
 * clones (without `vless.aar`) build and run. See
 * [docs/VLESS_INTEGRATION.md](../../../../../../../../docs/VLESS_INTEGRATION.md)
 * for the full architecture.
 */
class VlessTunnel(
    private val adapter: XrayAdapter = NoopXrayAdapter,
    private val tun2socks: Tun2SocksBridge = NoopTun2SocksBridge,
    private val socksPort: Int = DEFAULT_SOCKS_PORT,
    private val clock: () -> Long = System::currentTimeMillis,
) : VpnTunnel {
    override val supportedProtocol: Protocol = Protocol.VLESS

    @Volatile
    private var running = false

    override suspend fun start(config: ConnectionConfig, tunFd: Int, protector: Protector) {
        require(config is ConnectionConfig.Vless) { "VlessTunnel requires ConnectionConfig.Vless" }
        check(!running) { "VLESS tunnel already running" }

        val xrayConfig = XrayConfigBuilder.build(config, socksPort).toString()

        if (!adapter.available()) {
            Timber.tag(TAG).w("vless.aar missing — VLESS running in noop mode")
            running = true
            return
        }
        if (!tun2socks.available()) {
            Timber.tag(TAG).w("tun2socks bridge missing — VLESS running in noop mode")
            running = true
            return
        }

        adapter.startXray(xrayConfig, protector)
        tun2socks.start(tunFd, BRIDGE_HOST, socksPort)
        running = true
    }

    override suspend fun stop() {
        if (!running) return
        running = false
        if (tun2socks.available()) runCatching { tun2socks.stop() }
        if (adapter.available()) runCatching { adapter.stopXray() }
    }

    override fun stats(): Flow<TunnelStats> =
        flow { emit(TunnelStats.ZERO.copy(sampledAtEpochMs = clock())) }

    /**
     * Hides the Xray gomobile binding behind a tiny interface so we can test
     * without it. Implementations own the Xray-core lifecycle; the caller
     * owns tun2socks and routing.
     */
    interface XrayAdapter {
        fun available(): Boolean

        /**
         * Starts Xray-core with [configJson]. [protector] must be invoked by
         * Xray on every outbound socket before use — the default VLESS config
         * has one `vless` outbound, but TLS/Reality may open more.
         */
        fun startXray(configJson: String, protector: Protector)

        fun stopXray()
    }

    /**
     * Pumps packets between the Android TUN file descriptor and a local
     * SOCKS5 endpoint. Implementations will wrap a gomobile-bound fork of
     * `xjasonlyu/tun2socks` or `hev-socks5-tunnel`.
     */
    interface Tun2SocksBridge {
        fun available(): Boolean

        fun start(tunFd: Int, socksHost: String, socksPort: Int)

        fun stop()
    }

    private object NoopXrayAdapter : XrayAdapter {
        override fun available(): Boolean = false

        override fun startXray(configJson: String, protector: Protector) = Unit

        override fun stopXray() = Unit
    }

    private object NoopTun2SocksBridge : Tun2SocksBridge {
        override fun available(): Boolean = false

        override fun start(tunFd: Int, socksHost: String, socksPort: Int) = Unit

        override fun stop() = Unit
    }

    companion object {
        const val DEFAULT_SOCKS_PORT: Int = 10808
        const val BRIDGE_HOST: String = "127.0.0.1"
        private const val TAG = "VlessTunnel"
    }
}
