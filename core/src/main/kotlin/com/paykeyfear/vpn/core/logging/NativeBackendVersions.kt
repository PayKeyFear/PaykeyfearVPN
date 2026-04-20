package com.paykeyfear.vpn.core.logging

import timber.log.Timber

/**
 * Reflective lookup of the bundled native backend versions.
 *
 * The three protocol Go wrappers are compiled into a single umbrella
 * `paykeyfearnative.aar` (see `third_party/gomobile-bundle`); that .aar
 * exposes `awgVersion()`, `vlessVersion()`, `hy2Version()` on the Java
 * class `paykeyfearnative.Paykeyfearnative`. When the .aar is missing
 * (fresh checkout, JVM tests) `Class.forName` throws and we return null
 * so the About screen renders a "not bundled" badge instead of crashing.
 *
 * Lives in :core (not :protocols:*) so the About screen in :app can
 * surface all three versions without taking dependencies on every
 * protocol module's internals.
 */
object NativeBackendVersions {
    private const val TAG = "NativeBackendVersions"
    private const val BUNDLE_CLASS = "paykeyfearnative.Paykeyfearnative"

    /**
     * Public diagnostic: returns a human-readable reason why the native
     * bundle is not available, or null if everything loaded fine. The
     * About screen displays this as a subtitle under "not bundled".
     */
    @Volatile
    var lastError: String? = null
        private set

    fun awg(): String? = invokeStatic("awgVersion")

    fun vless(): String? = invokeStatic("vlessVersion")

    fun hysteria2(): String? = invokeStatic("hy2Version")

    private fun invokeStatic(methodName: String): String? {
        return try {
            val cls = Class.forName(BUNDLE_CLASS)
            val m = cls.getMethod(methodName)
            val result = m.invoke(null) as? String
            lastError = null
            result
        } catch (e: ClassNotFoundException) {
            lastError = "ClassNotFoundException: $BUNDLE_CLASS " +
                "(umbrella .aar missing from APK — check app/libs/paykeyfearnative.jar)"
            Timber.tag(TAG).w("Class.forName(%s) failed: %s", BUNDLE_CLASS, e.message)
            null
        } catch (e: NoSuchMethodError) {
            lastError = "NoSuchMethodError: $methodName (stale .aar?)"
            Timber.tag(TAG).w(e, "Method %s missing on %s", methodName, BUNDLE_CLASS)
            null
        } catch (e: UnsatisfiedLinkError) {
            lastError = "UnsatisfiedLinkError: ${e.message} " +
                "(libgojni.so missing for this ABI — check jniLibs)"
            Timber.tag(TAG).w(e, "Native library failed to load for %s", methodName)
            null
        } catch (e: Throwable) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Timber.tag(TAG).w(e, "%s lookup failed", methodName)
            null
        }
    }
}
