package com.paykeyfear.vpn.protocols.vless

import android.net.TrafficStats
import android.os.Process
import com.paykeyfear.vpn.core.Protector
import com.paykeyfear.vpn.core.VpnTunnel
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.core.model.TunnelStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class VlessTunnel(
    private val adapter: XrayAdapter = NoopXrayAdapter,
    private val tun2socks: Tun2SocksBridge = NoopTun2SocksBridge,
    private val socksPort: Int = DEFAULT_SOCKS_PORT,
    private val clock: () -> Long = System::currentTimeMillis,
) : VpnTunnel {
    override val supportedProtocol: Protocol = Protocol.VLESS

    @Volatile private var running = false

    // Baseline bytes sampled at the moment the tunnel starts, so we
    // report bytes transferred *this session* rather than cumulative.
    @Volatile private var baselineRx: Long = 0L

    @Volatile private var baselineTx: Long = 0L

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

        // Capture baseline before traffic starts flowing.
        val uid = Process.myUid()
        baselineRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
        baselineTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)

        adapter.startXray(xrayConfig, protector)
        tun2socks.start(tunFd, BRIDGE_HOST, socksPort)
        running = true
    }

    override suspend fun stop() {
        if (!running) return
        running = false
        baselineRx = 0L
        baselineTx = 0L
        // tun2socks.stop() and adapter.stopXray() are blocking JNI calls that
        // may hang if the Go runtime is stuck (e.g. tun2socks engine shutdown
        // waiting on a QUIC context). Run each on a daemon thread and join with
        // a hard timeout so a misbehaving Go stop doesn't block the Kotlin side
        // indefinitely and cause repeated ACTION_STOP calls from the service.
        stopWithTimeout("tun2socks") { if (tun2socks.available()) tun2socks.stop() }
        stopWithTimeout("xray") { if (adapter.available()) adapter.stopXray() }
    }

    private fun stopWithTimeout(name: String, block: () -> Unit) {
        val t = Thread(block, "vless-stop-$name").also {
            it.isDaemon = true
            it.start()
        }
        t.join(STOP_TIMEOUT_MS)
        if (t.isAlive) {
            Timber.tag(TAG).w("$name stop timed out after ${STOP_TIMEOUT_MS}ms — continuing")
        }
    }

    override fun stats(): Flow<TunnelStats> = flow {
        val uid = Process.myUid()
        val rx = (TrafficStats.getUidRxBytes(uid).coerceAtLeast(0) - baselineRx).coerceAtLeast(0)
        val tx = (TrafficStats.getUidTxBytes(uid).coerceAtLeast(0) - baselineTx).coerceAtLeast(0)
        emit(TunnelStats(rxBytes = rx, txBytes = tx, sampledAtEpochMs = clock()))
    }

    interface XrayAdapter {
        fun available(): Boolean
        fun startXray(configJson: String, protector: Protector)
        fun stopXray()
    }

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
        private const val STOP_TIMEOUT_MS = 3_000L
    }
}
