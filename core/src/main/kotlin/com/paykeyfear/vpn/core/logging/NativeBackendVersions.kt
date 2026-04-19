package com.paykeyfear.vpn.core.logging

/**
 * Reflective lookup of the bundled native backend versions.
 *
 * The three protocol Go wrappers are compiled into a single umbrella
 * `paykeyfearnative.aar` (see `third_party/gomobile-bundle`); that .aar
 * exposes `AwgVersion()`, `VlessVersion()`, `Hy2Version()` on the Java
 * class `paykeyfearnative.Paykeyfearnative`. When the .aar is missing
 * (fresh checkout, JVM tests) `Class.forName` throws and we return null
 * so the About screen renders a "not bundled" badge instead of crashing.
 *
 * Lives in :core (not :protocols:*) so the About screen in :app can
 * surface all three versions without taking dependencies on every
 * protocol module's internals.
 */
object NativeBackendVersions {
    fun awg(): String? = invokeStatic("AwgVersion")

    fun vless(): String? = invokeStatic("VlessVersion")

    fun hysteria2(): String? = invokeStatic("Hy2Version")

    private fun invokeStatic(methodName: String): String? = runCatching {
        val cls = Class.forName("paykeyfearnative.Paykeyfearnative")
        val m = cls.getMethod(methodName)
        m.invoke(null) as? String
    }.getOrNull()
}
