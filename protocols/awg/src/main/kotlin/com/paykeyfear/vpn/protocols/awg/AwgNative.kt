package com.paykeyfear.vpn.protocols.awg

import timber.log.Timber
import java.lang.reflect.Method

/**
 * Bridge to the gomobile-generated `paykeyfearnative.Paykeyfearnative`
 * Java class. We bundle awg / vless / hysteria2 into a single Go module
 * so the resulting .aar ships ONE libgojni.so and avoids AGP duplicate-
 * classes / duplicate-.so failures (see third_party/gomobile-bundle).
 *
 * Uses reflection so the Kotlin module compiles whether or not the
 * bundle .aar is present on the classpath.
 *
 * Expected Java signatures (umbrella class, prefixed method names):
 * ```
 * package paykeyfearnative;
 * public class Paykeyfearnative {
 *     public static int    awgStart(String cfg, int tunFd);
 *     public static void   awgStop(int handle);
 *     public static String awgStats(int handle);
 *     public static String awgVersion();
 *     public static String awgLastError();
 *     public static void   setProtector(Protector p);  // shared across all protos
 * }
 * public interface Protector { boolean protect(int fd); }
 * ```
 */
internal object AwgNative {
    private val bridge: Bridge? = runCatching {
        val cls = Class.forName("paykeyfearnative.Paykeyfearnative")
        val protectorCls = Class.forName("paykeyfearnative.Protector")
        Bridge(
            start = cls.getMethod("awgStart", String::class.java, Int::class.javaPrimitiveType),
            stop = cls.getMethod("awgStop", Int::class.javaPrimitiveType),
            stats = cls.getMethod("awgStats", Int::class.javaPrimitiveType),
            lastError = cls.getMethod("awgLastError"),
            setProtector = cls.getMethod("setProtector", protectorCls),
            protectorCls = protectorCls,
        )
    }.onFailure {
        Timber.tag(TAG).d("paykeyfearnative.aar not on classpath (%s) — running in noop mode", it.javaClass.simpleName)
    }.getOrNull()

    val NATIVE_AVAILABLE: Boolean get() = bridge != null

    fun installProtector(protector: com.paykeyfear.vpn.core.Protector) {
        val b = bridge ?: return
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            b.protectorCls.classLoader,
            arrayOf(b.protectorCls),
        ) { _, method, args ->
            if (method.name == "protect" && args != null && args.isNotEmpty()) {
                protector.protect(args[0] as Int)
            } else {
                true
            }
        }
        b.setProtector.invoke(null, proxy)
    }

    fun startTunnel(config: String, tunFd: Int): Long {
        val b = bridge ?: return AwgTunnel.INVALID_HANDLE
        val h = (b.start.invoke(null, config, tunFd) as Int)
        if (h <= 0) {
            Timber.tag(TAG).e("awgmobile.Start failed: %s", b.lastError.invoke(null))
            return AwgTunnel.INVALID_HANDLE
        }
        return h.toLong()
    }

    fun stopTunnel(handle: Long) {
        bridge?.stop?.invoke(null, handle.toInt())
    }

    fun lastError(): String =
        bridge?.lastError?.invoke(null) as? String ?: ""

    fun getStats(handle: Long): LongArray {
        val raw = bridge?.stats?.invoke(null, handle.toInt()) as? String ?: return longArrayOf(0, 0)
        var rx = 0L
        var tx = 0L
        raw.lineSequence().forEach { line ->
            when {
                line.startsWith("rx=") -> rx = line.substringAfter("rx=").trim().toLongOrNull() ?: 0
                line.startsWith("tx=") -> tx = line.substringAfter("tx=").trim().toLongOrNull() ?: 0
            }
        }
        return longArrayOf(rx, tx)
    }

    private class Bridge(
        val start: Method,
        val stop: Method,
        val stats: Method,
        val lastError: Method,
        val setProtector: Method,
        val protectorCls: Class<*>,
    )

    private const val TAG = "AwgNative"
}
