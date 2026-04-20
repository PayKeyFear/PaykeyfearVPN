package com.paykeyfear.vpn.protocols.awg

import com.paykeyfear.vpn.core.Protector
import com.paykeyfear.vpn.core.VpnTunnel
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.core.model.TunnelStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class AwgTunnel(
    private val native: Native = JniNative,
    private val clock: () -> Long = System::currentTimeMillis,
) : VpnTunnel {
    override val supportedProtocol: Protocol = Protocol.AWG

    @Volatile
    private var handle: Long = INVALID_HANDLE

    // Protect hook is installed into awgmobile before Start; amneziawg-go's
    // default Bind currently owns its UDP sockets directly (see
    // third_party/awg-mobile/awgmobile.go), so this is a best-effort hook
    // for follow-up when a protected Bind lands.
    override suspend fun start(config: ConnectionConfig, tunFd: Int, protector: Protector) {
        require(config is ConnectionConfig.Awg) { "AwgTunnel requires ConnectionConfig.Awg" }
        check(handle == INVALID_HANDLE) { "AWG tunnel already running" }
        if (!native.available()) {
            Timber.tag(TAG).w("Native libawg not loaded — running in noop mode")
            handle = FAKE_HANDLE
            return
        }
        native.installProtector(protector)
        val rendered = AwgConfigRenderer.render(config)
        handle = native.start(rendered, tunFd)
        if (handle == INVALID_HANDLE) {
            val why = native.lastError().ifBlank { "no error reported" }
            error("amneziawg-go refused config: $why")
        }
    }

    override suspend fun stop() {
        val h = handle
        handle = INVALID_HANDLE
        if (h == INVALID_HANDLE || h == FAKE_HANDLE) return
        native.stop(h)
    }

    override fun stats(): Flow<TunnelStats> =
        flow {
            val h = handle
            if (h == INVALID_HANDLE || h == FAKE_HANDLE || !native.available()) {
                emit(TunnelStats.ZERO.copy(sampledAtEpochMs = clock()))
                return@flow
            }
            val arr = native.stats(h)
            emit(TunnelStats(rxBytes = arr.getOrElse(0) { 0 }, txBytes = arr.getOrElse(1) { 0 }, sampledAtEpochMs = clock()))
        }

    interface Native {
        fun available(): Boolean

        fun installProtector(protector: Protector) = Unit

        fun start(config: String, tunFd: Int): Long

        fun stop(handle: Long)

        fun stats(handle: Long): LongArray

        fun lastError(): String = ""
    }

    private object JniNative : Native {
        override fun available(): Boolean = AwgNative.NATIVE_AVAILABLE

        override fun installProtector(protector: Protector) = AwgNative.installProtector(protector)

        override fun start(config: String, tunFd: Int): Long = AwgNative.startTunnel(config, tunFd)

        override fun stop(handle: Long) = AwgNative.stopTunnel(handle)

        override fun stats(handle: Long): LongArray = AwgNative.getStats(handle)

        override fun lastError(): String = AwgNative.lastError()
    }

    companion object {
        const val INVALID_HANDLE: Long = 0L
        const val FAKE_HANDLE: Long = -1L
        private const val TAG = "AwgTunnel"
    }
}
