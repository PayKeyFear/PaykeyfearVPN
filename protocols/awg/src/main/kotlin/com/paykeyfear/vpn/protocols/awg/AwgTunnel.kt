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
    override suspend fun start(config: ConnectionConfig, tunFd: Int, protector: Protector, ruBypassEnabled: Boolean) {
        require(config is ConnectionConfig.Awg) { "AwgTunnel requires ConnectionConfig.Awg" }
        check(handle == INVALID_HANDLE) { "AWG tunnel already running" }
        if (!native.available()) {
            Timber.tag(TAG).w("Native libawg not loaded — running in noop mode")
            handle = FAKE_HANDLE
            return
        }
        native.installProtector(protector)
        val rendered = AwgConfigRenderer.render(config)
        Timber.tag(TAG).i("AWG config summary: %s", summarize(config, rendered))
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

    private fun summarize(config: ConnectionConfig.Awg, rendered: String): String {
        // Dump the UAPI-key checklist so we can tell at a glance which
        // AmneziaWG junk/header fields actually reached the device. Never
        // log the rendered config directly — it contains private_key.
        val j = config.junk
        val hasLine = { k: String -> rendered.lineSequence().any { it.startsWith("$k=") } }
        return buildString {
            append("endpoint=").append(config.endpoint.host).append(':').append(config.endpoint.port)
            append(" peerPubKeySet=").append(config.peerPublicKey.isNotBlank())
            append(" psk=").append(config.presharedKey != null)
            append(" addrs=").append(config.addresses.size)
            append(" dns=").append(if (config.dns.isEmpty()) "<empty>" else config.dns.joinToString(","))
            append(" allowed=").append(config.allowedIps.size)
            append(" mtu=").append(config.mtu ?: "default")
            append(" keepalive=").append(config.persistentKeepalive ?: "none")
            append(" jc=").append(j.jc).append(" jmin=").append(j.jmin).append(" jmax=").append(j.jmax)
            append(" s1=").append(j.s1).append(" s2=").append(j.s2)
            append(" s3=").append(j.s3).append(" s4=").append(j.s4)
            append(" h=").append(listOf(j.h1, j.h2, j.h3, j.h4).count { !it.isNullOrBlank() }).append("/4")
            append(" i=").append(listOf(j.i1, j.i2, j.i3, j.i4, j.i5).count { !it.isNullOrBlank() }).append("/5")
            append(" jN=").append(listOf(j.j1, j.j2, j.j3).count { !it.isNullOrBlank() }).append("/3")
            append(" itime=").append(j.itime)
            append(" rendered[h1-h4]=")
            append(if (hasLine("h1") && hasLine("h2") && hasLine("h3") && hasLine("h4")) "all" else "MISSING")
        }
    }

    companion object {
        const val INVALID_HANDLE: Long = 0L
        const val FAKE_HANDLE: Long = -1L
        private const val TAG = "AwgTunnel"
    }
}
