package com.paykeyfear.vpn.config.hysteria2

import com.paykeyfear.vpn.config.ConfigParseException
import com.paykeyfear.vpn.config.ConfigParser
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.core.model.BandwidthConfig
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint
import com.paykeyfear.vpn.core.model.ObfsConfig
import com.paykeyfear.vpn.core.model.Protocol
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/**
 * Parses Hysteria2 inputs in either form:
 *  - URI:  `hysteria2://<password>@host:port?sni=...&insecure=1#Label`
 *  - flat YAML (subset): `server: host:port\nauth: pw\ntls:\n  sni: ...\n  insecure: true`
 *
 * Full YAML is deliberately parsed by a hand-rolled subset parser to avoid
 * pulling a YAML dependency into this module. Nested keys are resolved via
 * indentation (2 spaces).
 */
class Hysteria2YamlParser : ConfigParser {
    override val protocol: Protocol = Protocol.HYSTERIA2

    override fun canParse(source: ConfigSource): Boolean {
        val text = source.text()?.trim() ?: return false
        if (text.startsWith(URI_SCHEME)) return true
        return text.contains("server:") && (text.contains("auth:") || text.contains("password:"))
    }

    override fun parse(source: ConfigSource): ConnectionConfig {
        val text = source.text()?.trim() ?: throw ConfigParseException("Hysteria2 parser needs text input")
        return if (text.startsWith(URI_SCHEME)) parseUri(text, source.name) else parseYaml(text, source.name)
    }

    private fun parseUri(text: String, fallbackName: String): ConnectionConfig.Hysteria2 {
        val uri = runCatching { URI(text) }.getOrElse { throw ConfigParseException("Invalid Hysteria2 URI", it) }
        val password = uri.userInfo?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            ?: throw ConfigParseException("Hysteria2 URI missing password")
        val host = uri.host ?: throw ConfigParseException("Hysteria2 URI missing host")
        val port = uri.port.takeIf { it > 0 } ?: throw ConfigParseException("Hysteria2 URI missing port")
        val q = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val idx = part.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
            URLDecoder.decode(part.substring(0, idx), Charsets.UTF_8) to
                URLDecoder.decode(part.substring(idx + 1), Charsets.UTF_8)
        }.toMap()
        val label = uri.fragment?.let { URLDecoder.decode(it, Charsets.UTF_8) } ?: fallbackName
        val obfs = q["obfs"]?.let { type -> ObfsConfig(type, q["obfs-password"].orEmpty()) }
        return ConnectionConfig.Hysteria2(
            id = UUID.nameUUIDFromBytes(("hy2:$host:$port:$password").toByteArray()).toString(),
            displayName = label,
            endpoint = Endpoint(host, port),
            password = password,
            sni = q["sni"],
            insecure = q["insecure"]?.equals("1") == true || q["insecure"]?.equals("true", ignoreCase = true) == true,
            obfs = obfs,
            pinSha256 = q["pinSHA256"],
        )
    }

    private fun parseYaml(text: String, fallbackName: String): ConnectionConfig.Hysteria2 {
        val tree = SimpleYaml.parse(text)
        val server = tree.string("server") ?: throw ConfigParseException("Missing 'server' in Hysteria2 yaml")
        val endpoint = Endpoint.parse(server)
        val password = tree.string("auth") ?: tree.string("password")
            ?: throw ConfigParseException("Missing 'auth'/'password' in Hysteria2 yaml")
        val tls = tree.child("tls")
        val obfsNode = tree.child("obfs")
        val obfs = obfsNode?.string("type")?.let { type ->
            val pw = obfsNode.child(type)?.string("password") ?: obfsNode.string("password").orEmpty()
            ObfsConfig(type, pw)
        }
        val bandwidth = tree.child("bandwidth")?.let {
            BandwidthConfig(up = it.string("up"), down = it.string("down"))
        }
        return ConnectionConfig.Hysteria2(
            id = UUID.nameUUIDFromBytes(("hy2:$server:$password").toByteArray()).toString(),
            displayName = fallbackName,
            endpoint = endpoint,
            password = password,
            sni = tls?.string("sni"),
            insecure = tls?.bool("insecure") ?: false,
            obfs = obfs,
            bandwidth = bandwidth,
            pinSha256 = tls?.string("pinSHA256"),
        )
    }

    private fun ConfigSource.text(): String? =
        when (this) {
            is ConfigSource.Text -> content
            is ConfigSource.Bytes -> runCatching { content.toString(Charsets.UTF_8) }.getOrNull()
        }

    private companion object {
        const val URI_SCHEME = "hysteria2://"
    }
}
