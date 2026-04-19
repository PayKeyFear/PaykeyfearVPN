package com.paykeyfear.vpn.config.vless

import com.paykeyfear.vpn.config.ConfigParseException
import com.paykeyfear.vpn.config.ConfigParser
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint
import com.paykeyfear.vpn.core.model.Protocol
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/**
 * Parses VLESS share-link URIs: `vless://<uuid>@<host>:<port>?...#<label>`
 *
 * Follows the de-facto `v2rayN` / Xray schema. Query parameters
 * (type, security, sni, pbk, sid, fp, path, serviceName) are kept as-is.
 */
class VlessUriParser : ConfigParser {
    override val protocol: Protocol = Protocol.VLESS

    override fun canParse(source: ConfigSource): Boolean {
        val text = source.text()?.trim() ?: return false
        return text.startsWith(SCHEME) && text.contains('@')
    }

    override fun parse(source: ConfigSource): ConnectionConfig {
        val text = source.text()?.trim() ?: throw ConfigParseException("VLESS parser needs text input")
        require(text.startsWith(SCHEME)) { "VLESS URI must start with $SCHEME" }
        val uri = runCatching { URI(text) }.getOrElse { throw ConfigParseException("Invalid VLESS URI", it) }

        val userInfo = uri.userInfo ?: throw ConfigParseException("Missing VLESS UUID")
        val host = uri.host ?: throw ConfigParseException("Missing VLESS host")
        val port = uri.port.takeIf { it > 0 } ?: throw ConfigParseException("Missing VLESS port")

        val params = parseQuery(uri.rawQuery.orEmpty())
        val fragment = uri.fragment?.let { URLDecoder.decode(it, Charsets.UTF_8) }

        return ConnectionConfig.Vless(
            id = UUID.nameUUIDFromBytes(("vless:$userInfo@$host:$port").toByteArray()).toString(),
            displayName = fragment ?: source.name,
            endpoint = Endpoint(host, port),
            userId = userInfo,
            flow = params["flow"],
            encryption = params["encryption"] ?: "none",
            network = params["type"] ?: "tcp",
            security = params["security"],
            sni = params["sni"] ?: params["host"],
            publicKey = params["pbk"],
            shortId = params["sid"],
            fingerprint = params["fp"],
            path = params["path"],
            serviceName = params["serviceName"],
        )
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isEmpty()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val k = URLDecoder.decode(part.substring(0, idx), Charsets.UTF_8)
            val v = URLDecoder.decode(part.substring(idx + 1), Charsets.UTF_8)
            k to v
        }.toMap()
    }

    private fun ConfigSource.text(): String? =
        when (this) {
            is ConfigSource.Text -> content
            is ConfigSource.Bytes -> runCatching { content.toString(Charsets.UTF_8) }.getOrNull()
        }

    private companion object {
        const val SCHEME = "vless://"
    }
}
