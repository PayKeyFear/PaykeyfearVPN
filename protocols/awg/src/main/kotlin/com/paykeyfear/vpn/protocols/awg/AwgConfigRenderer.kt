package com.paykeyfear.vpn.protocols.awg

import com.paykeyfear.vpn.core.model.ConnectionConfig

/**
 * Renders an [ConnectionConfig.Awg] into the wgQuickConfig string expected by
 * amneziawg-go's `IpcSet` protocol.
 *
 * See: https://github.com/amnezia-vpn/amneziawg-go (IPC section).
 */
object AwgConfigRenderer {
    fun render(config: ConnectionConfig.Awg): String =
        buildString {
            appendLine("private_key=${config.privateKey}")
            config.mtu?.let { appendLine("mtu=$it") }
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
            appendLine("public_key=${config.peerPublicKey}")
            config.presharedKey?.let { appendLine("preshared_key=$it") }
            appendLine("endpoint=${config.endpoint.host}:${config.endpoint.port}")
            config.persistentKeepalive?.let { appendLine("persistent_keepalive_interval=$it") }
            config.allowedIps.forEach { appendLine("allowed_ip=$it") }
        }
}
