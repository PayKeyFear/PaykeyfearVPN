package com.paykeyfear.vpn.core.logging

/**
 * Reflective lookup of the bundled native backend versions.
 *
 * Each gomobile-generated `.aar` exposes a `Version()` static method on
 * its package class (`awgmobile.Awgmobile`, `vlessmobile.Vlessmobile`,
 * `hy2mobile.Hy2mobile`). When the `.aar` is missing from the classpath
 * the lookup returns `null`, letting the UI render a "noop" badge
 * instead of crashing.
 *
 * Lives in :core (not :protocols:*) so the About screen in :app can
 * surface all three versions without taking dependencies on every
 * protocol module's internals.
 */
object NativeBackendVersions {
    fun awg(): String? = invokeStatic("awgmobile.Awgmobile", "GetVersion")

    fun vless(): String? = invokeStatic("vlessmobile.Vlessmobile", "Version")

    fun hysteria2(): String? = invokeStatic("hy2mobile.Hy2mobile", "Version")

    private fun invokeStatic(className: String, methodName: String): String? = runCatching {
        val cls = Class.forName(className)
        val m = cls.getMethod(methodName)
        m.invoke(null) as? String
    }.getOrNull()
}
