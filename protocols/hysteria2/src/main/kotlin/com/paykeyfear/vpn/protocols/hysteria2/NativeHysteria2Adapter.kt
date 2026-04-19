package com.paykeyfear.vpn.protocols.hysteria2

import com.paykeyfear.vpn.core.Protector

/**
 * Real [Hysteria2Tunnel.Hysteria2Adapter] backed by `hysteria.aar`. Delegates
 * to [Hysteria2Native]; when the .aar is absent, [available] is false and the
 * tunnel uses its noop path.
 */
class NativeHysteria2Adapter : Hysteria2Tunnel.Hysteria2Adapter {
    @Volatile
    private var handle: String? = null

    override fun available(): Boolean = Hysteria2Native.NATIVE_AVAILABLE

    override fun start(yaml: String, tunFd: Int, protector: Protector) {
        Hysteria2Native.installProtector(protector)
        val h = Hysteria2Native.start(yaml, tunFd)
            ?: error("hy2mobile.Start returned empty handle")
        handle = h
    }

    override fun stop() {
        val h = handle ?: return
        handle = null
        Hysteria2Native.stop(h)
    }

    override fun stats(): LongArray {
        val h = handle ?: return longArrayOf(0L, 0L)
        return Hysteria2Native.stats(h)
    }
}
