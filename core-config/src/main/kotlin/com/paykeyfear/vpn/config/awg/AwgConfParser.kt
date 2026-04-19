package com.paykeyfear.vpn.config.awg

import com.paykeyfear.vpn.config.ConfigParseException
import com.paykeyfear.vpn.config.ConfigParser
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.core.model.AwgJunkParams
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint
import com.paykeyfear.vpn.core.model.Protocol
import java.util.UUID

/**
 * Parses AmneziaWG / WireGuard `.conf` format:
 *
 * ```
 * [Interface]
 * PrivateKey = ...
 * Address = 10.0.0.2/32
 * DNS = 1.1.1.1
 * MTU = 1420
 * Jc = 4                 # AmneziaWG junk-packet count
 * Jmin = 40
 * Jmax = 70
 * S1 = 50
 * S2 = 100
 * H1 = 1234567890
 * H2 = ...
 * H3 = ...
 * H4 = ...
 *
 * [Peer]
 * PublicKey = ...
 * PresharedKey = ...
 * AllowedIPs = 0.0.0.0/0, ::/0
 * Endpoint = example.com:51820
 * PersistentKeepalive = 25
 * ```
 */
class AwgConfParser : ConfigParser {
    override val protocol: Protocol = Protocol.AWG

    override fun canParse(source: ConfigSource): Boolean {
        val text = source.text() ?: return false
        return INTERFACE_HEADER.containsMatchIn(text) && PEER_HEADER.containsMatchIn(text)
    }

    override fun parse(source: ConfigSource): ConnectionConfig {
        val text = source.text() ?: throw ConfigParseException("AWG parser requires text input")
        val sections = parseSections(text)
        val iface = sections["interface"] ?: throw ConfigParseException("Missing [Interface] section")
        val peer = sections["peer"] ?: throw ConfigParseException("Missing [Peer] section")

        val privateKey = iface["privatekey"] ?: throw ConfigParseException("Interface.PrivateKey missing")
        val addresses = iface["address"]?.splitCsv().orEmpty()
        val dns = iface["dns"]?.splitCsv().orEmpty()
        val mtu = iface["mtu"]?.toIntOrNull()

        val peerPublicKey = peer["publickey"] ?: throw ConfigParseException("Peer.PublicKey missing")
        val presharedKey = peer["presharedkey"]
        val allowed = peer["allowedips"]?.splitCsv().orEmpty().ifEmpty { listOf("0.0.0.0/0", "::/0") }
        val endpointRaw = peer["endpoint"] ?: throw ConfigParseException("Peer.Endpoint missing")
        val persistentKeepalive = peer["persistentkeepalive"]?.toIntOrNull()

        val junk =
            AwgJunkParams(
                jc = iface["jc"]?.toIntOrNull(),
                jmin = iface["jmin"]?.toIntOrNull(),
                jmax = iface["jmax"]?.toIntOrNull(),
                s1 = iface["s1"]?.toIntOrNull(),
                s2 = iface["s2"]?.toIntOrNull(),
                h1 = iface["h1"]?.toLongOrNull(),
                h2 = iface["h2"]?.toLongOrNull(),
                h3 = iface["h3"]?.toLongOrNull(),
                h4 = iface["h4"]?.toLongOrNull(),
            )

        return ConnectionConfig.Awg(
            id = UUID.nameUUIDFromBytes(("awg:" + peerPublicKey + endpointRaw).toByteArray()).toString(),
            displayName = source.name,
            endpoint = Endpoint.parse(endpointRaw),
            privateKey = privateKey,
            peerPublicKey = peerPublicKey,
            presharedKey = presharedKey,
            addresses = addresses,
            dns = dns,
            allowedIps = allowed,
            mtu = mtu,
            persistentKeepalive = persistentKeepalive,
            junk = junk,
        )
    }

    private fun parseSections(text: String): Map<String, Map<String, String>> {
        val sections = LinkedHashMap<String, MutableMap<String, String>>()
        var current: String? = null
        for (rawLine in text.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val sectionMatch = SECTION_RE.matchEntire(line)
            if (sectionMatch != null) {
                current = sectionMatch.groupValues[1].lowercase()
                sections.getOrPut(current) { LinkedHashMap() }
                continue
            }
            val key = current ?: throw ConfigParseException("Key-value outside section: $line")
            val idx = line.indexOf('=')
            if (idx <= 0) throw ConfigParseException("Invalid entry: $line")
            val k = line.substring(0, idx).trim().lowercase()
            val v = line.substring(idx + 1).trim()
            sections.getValue(key)[k] = v
        }
        return sections
    }

    private fun String.splitCsv(): List<String> =
        split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun ConfigSource.text(): String? =
        when (this) {
            is ConfigSource.Text -> content
            is ConfigSource.Bytes -> runCatching { content.toString(Charsets.UTF_8) }.getOrNull()
        }

    private companion object {
        val INTERFACE_HEADER = Regex("""(?im)^\s*\[Interface]""")
        val PEER_HEADER = Regex("""(?im)^\s*\[Peer]""")
        val SECTION_RE = Regex("""\[([A-Za-z]+)]""")
    }
}
