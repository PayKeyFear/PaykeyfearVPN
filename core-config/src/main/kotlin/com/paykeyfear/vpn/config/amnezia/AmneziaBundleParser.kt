package com.paykeyfear.vpn.config.amnezia

import com.paykeyfear.vpn.config.ConfigParseException
import com.paykeyfear.vpn.config.ConfigParser
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.config.awg.AwgConfParser
import com.paykeyfear.vpn.config.awg.AwgJsonToConf
import com.paykeyfear.vpn.config.hysteria2.Hysteria2YamlParser
import com.paykeyfear.vpn.config.vless.VlessUriParser
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater

/**
 * Parses AmneziaVPN `.vpn` bundle files.
 *
 * AmneziaVPN's desktop & mobile apps emit configs as `vpn://` URIs whose
 * payload is **base64url(4-byte BE size + zlib(JSON))**. Older builds — and
 * our own test fixtures — sometimes emit plain base64(JSON) without the
 * size/zlib envelope, so both forms are accepted.
 *
 * Inside the JSON, each entry of `containers` is keyed by the protocol name
 * (`awg`, `wireguard`, `xray`, `vless`, `hysteria2`). The legacy shape used
 * a `container` field with a value like `"amnezia-awg"` — that form is also
 * accepted for back-compat. The inner `last_config` is either a protocol-
 * specific text blob (WireGuard `.conf`, VLESS URI, Hysteria2 YAML) or a
 * JSON object (AmneziaVPN's current AWG format); we sniff and route.
 */
class AmneziaBundleParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
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
        val containers = root["containers"] as? JsonArray
            ?: throw ConfigParseException("Missing 'containers' array")

        val defaultContainer = root["defaultContainer"]?.jsonPrimitive?.contentOrNull
        val selected = pickContainer(containers, defaultContainer)
            ?: throw ConfigParseException("No suitable container found")

        val protoKey = detectProtocolKey(selected)
            ?: throw ConfigParseException("Unsupported Amnezia container: no known protocol key")
        val inner = innerObject(selected, protoKey)
        val innerConfig = innerConfigText(selected, inner, protoKey)
        val label = root["description"]?.jsonPrimitive?.contentOrNull ?: source.name
        val innerSource = ConfigSource.Text(label, innerConfig)
        return when (protoKey) {
            "awg", "wireguard" -> awg.parse(innerSource)
            "xray", "vless" -> vless.parse(innerSource)
            "hysteria2", "hysteria" -> hy2.parse(innerSource)
            else -> throw ConfigParseException("Unsupported Amnezia protocol: $protoKey")
        }
    }

    private fun pickContainer(containers: JsonArray, defaultName: String?): JsonObject? {
        val objs = containers.mapNotNull { it as? JsonObject }
        if (objs.isEmpty()) return null
        if (defaultName != null) {
            val match = objs.firstOrNull { obj ->
                val legacy = obj["container"]?.jsonPrimitive?.contentOrNull
                if (legacy != null) return@firstOrNull legacy == defaultName
                // Real AmneziaVPN: the default looks like "amnezia-awg"; the
                // container key itself is just "awg". Match by suffix.
                obj.keys.any { key ->
                    key in KNOWN_PROTO_KEYS && defaultName.endsWith(key, ignoreCase = true)
                }
            }
            if (match != null) return match
        }
        return objs.first()
    }

    private fun detectProtocolKey(container: JsonObject): String? {
        // Prefer explicit legacy field if present.
        container["container"]?.jsonPrimitive?.contentOrNull?.let { name ->
            val lower = name.lowercase()
            KNOWN_PROTO_KEYS.firstOrNull { lower.contains(it) }?.let { return it }
        }
        // Otherwise detect by the container's own keys.
        return container.keys.firstOrNull { it in KNOWN_PROTO_KEYS }
    }

    private fun innerObject(container: JsonObject, protoKey: String): JsonObject? =
        container[protoKey] as? JsonObject

    private fun innerConfigText(container: JsonObject, inner: JsonObject?, protoKey: String): String {
        // AmneziaVPN typically nests last_config inside the per-protocol
        // object; legacy bundles put it at the container level.
        val lastConfig = (inner?.get("last_config") ?: container["last_config"])
            ?.jsonPrimitive?.contentOrNull
        if (lastConfig != null && lastConfig.isNotBlank()) {
            return if (protoKey in AWG_KEYS && lastConfig.trimStart().startsWith('{')) {
                AwgJsonToConf.convert(lastConfig)
            } else {
                lastConfig
            }
        }
        // No last_config string — synthesise from the inner object itself,
        // which AmneziaVPN includes as a fully-expanded JSON representation.
        if (inner != null && protoKey in AWG_KEYS) {
            return AwgJsonToConf.convert(inner)
        }
        throw ConfigParseException("Missing last_config payload for $protoKey")
    }

    private fun decode(raw: String): String {
        val payload = if (raw.startsWith(SCHEME)) raw.removePrefix(SCHEME) else raw
        if (looksLikeJson(payload)) return payload
        val bytes = decodeBase64Bytes(payload)
            ?: throw ConfigParseException("Failed to base64-decode Amnezia bundle")
        // 1. Plain base64(JSON) — legacy and test fixtures.
        runCatching { bytes.toString(Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.trimStart().startsWith('{') && it.contains("\"containers\"") }
            ?.let { return it }
        // 2. AmneziaVPN wire format: 4-byte BE size + zlib(JSON).
        tryInflate(bytes)?.let { return it }
        throw ConfigParseException("Bundle is neither JSON, base64 JSON, nor size+zlib envelope")
    }

    private fun decodeBase64Bytes(payload: String): ByteArray? {
        val stripped = payload.replace("\\s".toRegex(), "")
        return runCatching { Base64.getUrlDecoder().decode(stripped) }
            .recoverCatching { Base64.getDecoder().decode(stripped) }
            .getOrNull()
    }

    private fun tryInflate(bytes: ByteArray): String? {
        if (bytes.size <= 6) return null
        // Size prefix is 4-byte big-endian; zlib stream starts at offset 4
        // and begins with 0x78 (RFC 1950 CMF byte). Accept any second byte
        // since AmneziaVPN has used 0x9C, 0xDA and 0x01 depending on build.
        if (bytes[4] != 0x78.toByte()) return null
        val inflater = Inflater()
        inflater.setInput(bytes, 4, bytes.size - 4)
        val out = ByteArrayOutputStream(bytes.size * 4)
        val buf = ByteArray(4096)
        return runCatching {
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) return@runCatching null
                    break
                }
                out.write(buf, 0, n)
            }
            val text = out.toString(Charsets.UTF_8.name())
            if (text.trimStart().startsWith('{') && text.contains("\"containers\"")) text else null
        }.getOrNull().also { inflater.end() }
    }

    private fun looksLikeJson(text: String): Boolean =
        text.startsWith('{') && text.contains("\"containers\"")

    private fun looksLikeBareBase64Bundle(text: String): Boolean {
        if (text.length < 32) return false
        if (!BASE64_RE.matches(text)) return false
        val bytes = decodeBase64Bytes(text) ?: return false
        val asText = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
        if (asText != null && asText.trimStart().startsWith('{') && asText.contains("\"containers\"")) return true
        return tryInflate(bytes) != null
    }

    private val JsonPrimitive.contentOrNull: String?
        get() = runCatching { content }.getOrNull()

    private fun ConfigSource.text(): String? =
        when (this) {
            is ConfigSource.Text -> content
            is ConfigSource.Bytes -> runCatching { content.toString(Charsets.UTF_8) }.getOrNull()
        }

    private companion object {
        const val SCHEME = "vpn://"
        val BASE64_RE = Regex("^[A-Za-z0-9+/_=-]+$")
        val AWG_KEYS = setOf("awg", "wireguard")
        val KNOWN_PROTO_KEYS = setOf("awg", "wireguard", "xray", "vless", "hysteria2", "hysteria")
    }
}
