package com.paykeyfear.vpn.config.amnezia

import com.paykeyfear.vpn.config.ConfigParseException
import com.paykeyfear.vpn.config.ConfigParser
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.config.awg.AwgConfParser
import com.paykeyfear.vpn.config.hysteria2.Hysteria2YamlParser
import com.paykeyfear.vpn.config.vless.VlessUriParser
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Parses AmneziaVPN `.vpn` bundle files.
 *
 * AmneziaVPN ships configs as JSON optionally wrapped in `vpn://...` base64.
 * The JSON body contains a `containers` array where the active element is
 * identified by `defaultContainer` and has a protocol-specific `last_config`
 * payload.
 */
class AmneziaBundleParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val awg: AwgConfParser = AwgConfParser(),
    private val vless: VlessUriParser = VlessUriParser(),
    private val hy2: Hysteria2YamlParser = Hysteria2YamlParser(),
) : ConfigParser {
    override val protocol: Protocol = Protocol.AWG

    override fun canParse(source: ConfigSource): Boolean {
        val text = source.text()?.trim() ?: return false
        return text.startsWith(SCHEME) ||
            looksLikeJson(text) ||
            looksLikeBareBase64Bundle(text)
    }

    override fun parse(source: ConfigSource): ConnectionConfig {
        val raw = source.text()?.trim() ?: throw ConfigParseException("Amnezia parser needs text input")
        val jsonText = decode(raw)
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }
            .getOrElse { throw ConfigParseException("Invalid Amnezia bundle JSON", it) }
        val containers = root["containers"]?.let { it as? kotlinx.serialization.json.JsonArray }
            ?: throw ConfigParseException("Missing 'containers' array")
        val defaultContainer = root["defaultContainer"]?.jsonPrimitive?.contentOrNull
        val selected = containers.firstOrNull { el ->
            val name = (el as? JsonObject)?.get("container")?.jsonPrimitive?.contentOrNull
            defaultContainer == null || name == defaultContainer
        }?.let { it as? JsonObject }
            ?: throw ConfigParseException("No suitable container found")
        val containerName = selected["container"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val innerConfig = selected["last_config"]?.jsonPrimitive?.contentOrNull
            ?: throw ConfigParseException("Missing last_config payload")
        val label = root["description"]?.jsonPrimitive?.contentOrNull ?: source.name
        val innerSource = ConfigSource.Text(label, innerConfig)
        return when {
            containerName.contains("awg", ignoreCase = true) ||
                containerName.contains("wireguard", ignoreCase = true) -> awg.parse(innerSource)
            containerName.contains("xray", ignoreCase = true) ||
                containerName.contains("vless", ignoreCase = true) -> vless.parse(innerSource)
            containerName.contains("hysteria", ignoreCase = true) -> hy2.parse(innerSource)
            else -> throw ConfigParseException("Unsupported Amnezia container: $containerName")
        }
    }

    private fun decode(raw: String): String {
        if (raw.startsWith(SCHEME)) {
            val payload = raw.removePrefix(SCHEME)
            return tryBase64(payload)
                ?: throw ConfigParseException("Failed to base64-decode Amnezia bundle")
        }
        if (looksLikeJson(raw)) return raw
        // Fall back: some QR generators emit the bare base64 payload
        // without the `vpn://` scheme prefix. AmneziaVPN's desktop apps
        // accept both forms, so we should too — otherwise users scan a
        // QR and get "no parser recognised config".
        return tryBase64(raw)
            ?: throw ConfigParseException("Bundle is neither JSON nor base64")
    }

    private fun tryBase64(payload: String): String? {
        val stripped = payload.replace("\\s".toRegex(), "")
        return runCatching { Base64.getUrlDecoder().decode(stripped).toString(Charsets.UTF_8) }
            .recoverCatching { Base64.getDecoder().decode(stripped).toString(Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.contains("\"containers\"") }
    }

    private fun looksLikeJson(text: String): Boolean =
        text.startsWith('{') && text.contains("\"containers\"")

    private fun looksLikeBareBase64Bundle(text: String): Boolean {
        if (text.length < 32) return false
        // Quick rejection: base64 is restricted to [A-Za-z0-9+/=_-] (URL
        // or standard alphabet); anything else is something else.
        if (!BASE64_RE.matches(text)) return false
        return tryBase64(text) != null
    }

    private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
        get() = runCatching { content }.getOrNull()

    private fun ConfigSource.text(): String? =
        when (this) {
            is ConfigSource.Text -> content
            is ConfigSource.Bytes -> runCatching { content.toString(Charsets.UTF_8) }.getOrNull()
        }

    private companion object {
        const val SCHEME = "vpn://"
        val BASE64_RE = Regex("^[A-Za-z0-9+/_=-]+$")
    }
}
