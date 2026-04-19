package com.paykeyfear.vpn.protocols.vless

import com.paykeyfear.vpn.core.Protector

/**
 * Real [VlessTunnel.XrayAdapter] backed by `vless.aar`. Delegates to
 * [VlessNative]; when the .aar is absent, [available] is false and the
 * tunnel uses its noop path.
 */
class NativeXrayAdapter : VlessTunnel.XrayAdapter {
    private var handle: Int = VlessNative.INVALID_HANDLE

    override fun available(): Boolean = VlessNative.NATIVE_AVAILABLE

    override fun startXray(configJson: String, protector: Protector) {
        VlessNative.installProtector(protector)
        val h = VlessNative.startXray(configJson)
        check(h != VlessNative.INVALID_HANDLE) { "vlessmobile.StartXray returned invalid handle" }
        handle = h
    }

    override fun stopXray() {
        if (handle == VlessNative.INVALID_HANDLE) return
        VlessNative.stopXray(handle)
        handle = VlessNative.INVALID_HANDLE
    }
}

/** Real [VlessTunnel.Tun2SocksBridge] backed by the bundled tun2socks engine. */
class NativeTun2SocksBridge : VlessTunnel.Tun2SocksBridge {
    private var handle: Int = VlessNative.INVALID_HANDLE

    override fun available(): Boolean = VlessNative.NATIVE_AVAILABLE

    override fun start(tunFd: Int, socksHost: String, socksPort: Int) {
        val h = VlessNative.tun2socksStart(tunFd, socksHost, socksPort)
        check(h != VlessNative.INVALID_HANDLE) { "vlessmobile.Tun2SocksStart returned invalid handle" }
        handle = h
    }

    override fun stop() {
        if (handle == VlessNative.INVALID_HANDLE) return
        VlessNative.tun2socksStop(handle)
        handle = VlessNative.INVALID_HANDLE
    }
}
