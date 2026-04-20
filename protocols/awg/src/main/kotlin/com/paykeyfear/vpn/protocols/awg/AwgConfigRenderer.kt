package com.paykeyfear.vpn.protocols.awg

import android.util.Base64
import com.paykeyfear.vpn.core.model.ConnectionConfig

/**
 * Renders an [ConnectionConfig.Awg] into the wgQuickConfig string expected by
 * amneziawg-go's `IpcSet` protocol.
 *
 * See: https://github.com/amnezia-vpn/amneziawg-go (IPC section).
 *
 * IMPORTANT: WireGuard's userspace IPC uses **hex**-encoded 32-byte keys
 * (`private_key=<64 hex chars>`), while wg-quick `.conf` files (and
 * therefore [ConnectionConfig.Awg.privateKey] / `peerPublicKey` /
 * `presharedKey`) carry the same bytes **base64**-encoded. We convert on
 * the way out — passing the raw base64 to `IpcSet` makes amneziawg-go
 * reject the config with an opaque "failed to set private_key" error,
 * which surfaces in the UI as a generic "AWG refused config".
 */
object AwgConfigRenderer {
    fun render(config: ConnectionConfig.Awg): String =
        buildString {
            appendLine("private_key=${toHexKey(config.privateKey)}")
            // MTU is NOT a valid WireGuard/Amnezia UAPI key — it's set on
            // the kernel netdev (or via VpnService.Builder.setMtu on Android)
            // and including it here makes IpcSet return -22.
            with(config.junk) {
                jc?.let { appendLine("jc=$it") }
                jmin?.let { appendLine("jmin=$it") }
                jmax?.let { appendLine("jmax=$it") }
                s1?.let { appendLine("s1=$it") }
                s2?.let { appendLine("s2=$it") }
                h1?.let { appendLine("h1=$it") }
                h2?.let { appendLine("h2=$it") }
                h3?.let { appendLine("h3=$it") }
                h4?.let { appendLine("h4=$it") }
            }
            appendLine("public_key=${toHexKey(config.peerPublicKey)}")
            config.presharedKey?.let { appendLine("preshared_key=${toHexKey(it)}") }
            appendLine("endpoint=${config.endpoint.host}:${config.endpoint.port}")
            config.persistentKeepalive?.let { appendLine("persistent_keepalive_interval=$it") }
            config.allowedIps.forEach { appendLine("allowed_ip=$it") }
        }

    /**
     * Decodes a 32-byte WireGuard key from base64 and re-encodes it as a
     * lowercase 64-char hex string. If the input is already hex (some
     * configs ship that way) it is returned lowercased unchanged.
     */
    internal fun toHexKey(key: String): String {
        val trimmed = key.trim()
        if (trimmed.length == 64 && trimmed.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            return trimmed.lowercase()
        }
        val bytes = runCatching { Base64.decode(trimmed, Base64.NO_WRAP or Base64.NO_PADDING) }
            .recoverCatching { Base64.decode(trimmed, Base64.DEFAULT) }
            .getOrElse { throw IllegalArgumentException("WireGuard key is neither valid base64 nor hex: $trimmed") }
        require(bytes.size == 32) { "WireGuard key must decode to 32 bytes, got ${bytes.size}" }
        val sb = StringBuilder(64)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
