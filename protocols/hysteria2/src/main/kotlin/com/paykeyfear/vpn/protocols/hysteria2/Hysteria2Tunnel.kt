package com.paykeyfear.vpn.protocols.hysteria2

import com.paykeyfear.vpn.core.Protector
import com.paykeyfear.vpn.core.VpnTunnel
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.core.model.TunnelStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class Hysteria2Tunnel(
    private val adapter: Hysteria2Adapter = NoopHysteria2Adapter,
    private val clock: () -> Long = System::currentTimeMillis,
) : VpnTunnel {
    override val supportedProtocol: Protocol = Protocol.HYSTERIA2

    @Volatile
    private var running = false

    override suspend fun start(config: ConnectionConfig, tunFd: Int, protector: Protector, ruBypassEnabled: Boolean) {
        require(config is ConnectionConfig.Hysteria2) { "Hysteria2Tunnel requires ConnectionConfig.Hysteria2" }
        check(!running) { "Hysteria2 tunnel already running" }
        val yaml = Hysteria2ConfigRenderer.render(config)
        if (!adapter.available()) {
            Timber.tag(TAG).w("libhysteria missing — Hysteria2 running in noop mode")
            running = true
            return
        }
        adapter.start(yaml, tunFd, protector)
        running = true
    }

    override suspend fun stop() {
        if (!running) return
        running = false
        if (adapter.available()) adapter.stop()
    }

    override fun stats(): Flow<TunnelStats> =
        flow {
            if (!running || !adapter.available()) {
                emit(TunnelStats.ZERO.copy(sampledAtEpochMs = clock()))
                return@flow
            }
            val (rx, tx) = adapter.stats().let {
                (it.getOrElse(0) { 0L }) to (it.getOrElse(1) { 0L })
            }
            emit(TunnelStats(rxBytes = rx, txBytes = tx, sampledAtEpochMs = clock()))
        }

    interface Hysteria2Adapter {
        fun available(): Boolean

        /**
         * Boots the Hysteria2 client with [yaml] (as produced by
         * [Hysteria2ConfigRenderer]) and takes ownership of [tunFd].
         * [protector] must be installed before the QUIC handshake begins —
         * otherwise the UDP socket routes back through the VPN and loops.
         */
        fun start(yaml: String, tunFd: Int, protector: Protector)

        fun stop()

        /** Returns `[rxBytes, txBytes]`. Default is zeros for noop/test adapters. */
        fun stats(): LongArray = longArrayOf(0L, 0L)
    }

    private object NoopHysteria2Adapter : Hysteria2Adapter {
        override fun available(): Boolean = false

        override fun start(yaml: String, tunFd: Int, protector: Protector) = Unit

        override fun stop() = Unit
    }

    private companion object {
        const val TAG = "Hysteria2Tunnel"
    }
}
