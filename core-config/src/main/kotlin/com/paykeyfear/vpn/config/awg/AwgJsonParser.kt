package com.paykeyfear.vpn.config.awg

import com.paykeyfear.vpn.config.ConfigParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Converts AmneziaVPN's JSON-shaped `last_config` payload into the canonical
 * AmneziaWG/WireGuard `.conf` text form so the existing [AwgConfParser] can
 * consume it. AmneziaVPN's desktop apps ship the inner config as a JSON
 * blob with fields like `client_priv_key`, `server_pub_key`, `hostName`,
 * `H1`..`H4`, `Jc`, etc. — rather than a WireGuard ini.
 */
internal object AwgJsonToConf {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun convert(jsonText: String): String {
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }
            .getOrElse { throw ConfigParseException("Invalid AWG JSON payload", it) }
        return build(root)
    }

    fun convert(obj: JsonObject): String = build(obj)

    private fun build(obj: JsonObject): String {
        val sb = StringBuilder()
        sb.append("[Interface]\n")
        obj.str("client_priv_key", "clientPrivKey", "private_key", "privateKey")
            ?.let { sb.append("PrivateKey = ").append(it).append('\n') }
        obj.str("client_ip", "clientIp", "address", "Address")
            ?.let { sb.append("Address = ").append(it).append('\n') }
        val dns = buildList {
            obj.str("dns1", "DNS1")?.let { add(it) }
            obj.str("dns2", "DNS2")?.let { add(it) }
            obj["dns"]?.let { el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.forEach(::add)
                    is JsonArray -> el.forEach { add(it.jsonPrimitive.content) }
                    else -> {}
                }
            }
        }
        if (dns.isNotEmpty()) sb.append("DNS = ").append(dns.joinToString(", ")).append('\n')
        obj.str("mtu", "MTU")?.let { sb.append("MTU = ").append(it).append('\n') }
        obj.str("Jc", "jc")?.let { sb.append("Jc = ").append(it).append('\n') }
        obj.str("Jmin", "jmin")?.let { sb.append("Jmin = ").append(it).append('\n') }
        obj.str("Jmax", "jmax")?.let { sb.append("Jmax = ").append(it).append('\n') }
        obj.str("S1", "s1")?.let { sb.append("S1 = ").append(it).append('\n') }
        obj.str("S2", "s2")?.let { sb.append("S2 = ").append(it).append('\n') }
        obj.str("S3", "s3")?.let { sb.append("S3 = ").append(it).append('\n') }
        obj.str("S4", "s4")?.let { sb.append("S4 = ").append(it).append('\n') }
        obj.str("H1", "h1")?.let { sb.append("H1 = ").append(it).append('\n') }
        obj.str("H2", "h2")?.let { sb.append("H2 = ").append(it).append('\n') }
        obj.str("H3", "h3")?.let { sb.append("H3 = ").append(it).append('\n') }
        obj.str("H4", "h4")?.let { sb.append("H4 = ").append(it).append('\n') }
        obj.str("I1", "i1")?.let { sb.append("I1 = ").append(it).append('\n') }
        obj.str("I2", "i2")?.let { sb.append("I2 = ").append(it).append('\n') }
        obj.str("I3", "i3")?.let { sb.append("I3 = ").append(it).append('\n') }
        obj.str("I4", "i4")?.let { sb.append("I4 = ").append(it).append('\n') }
        obj.str("I5", "i5")?.let { sb.append("I5 = ").append(it).append('\n') }
        obj.str("J1", "j1")?.let { sb.append("J1 = ").append(it).append('\n') }
        obj.str("J2", "j2")?.let { sb.append("J2 = ").append(it).append('\n') }
        obj.str("J3", "j3")?.let { sb.append("J3 = ").append(it).append('\n') }
        obj.str("Itime", "itime", "ITime")?.let { sb.append("Itime = ").append(it).append('\n') }

        sb.append('\n').append("[Peer]\n")
        obj.str("server_pub_key", "serverPubKey", "public_key", "publicKey")
            ?.let { sb.append("PublicKey = ").append(it).append('\n') }
        obj.str("psk_key", "preshared_key", "presharedKey")
            ?.let { if (it.isNotBlank()) sb.append("PresharedKey = ").append(it).append('\n') }

        val allowed = obj["allowed_ips"] ?: obj["allowedIPs"] ?: obj["AllowedIPs"]
        val allowedCsv = when (allowed) {
            is JsonArray -> allowed.joinToString(", ") { it.jsonPrimitive.content }
            is JsonPrimitive -> allowed.contentOrNull.orEmpty()
            else -> ""
        }.ifBlank { "0.0.0.0/0, ::/0" }
        sb.append("AllowedIPs = ").append(allowedCsv).append('\n')

        val host = obj.str("hostName", "host", "server", "endpoint_host")
            ?: throw ConfigParseException("AWG JSON: missing hostName")
        val port = obj.str("port", "endpoint_port")
            ?: throw ConfigParseException("AWG JSON: missing port")
        val endpoint = if (':' in host && !host.startsWith('[')) "[$host]:$port" else "$host:$port"
        sb.append("Endpoint = ").append(endpoint).append('\n')
        obj.str("persistent_keep_alive", "persistentKeepalive", "PersistentKeepalive")
            ?.let { sb.append("PersistentKeepalive = ").append(it).append('\n') }

        return sb.toString()
    }

    private fun JsonObject.str(vararg names: String): String? {
        for (n in names) {
            val el = this[n] ?: continue
            val p = el as? JsonPrimitive ?: continue
            val v = p.contentOrNull ?: continue
            if (v.isBlank() || v == "null") continue
            return v
        }
        return null
    }

    private val JsonPrimitive.contentOrNull: String?
        get() = runCatching { content }.getOrNull()
}
