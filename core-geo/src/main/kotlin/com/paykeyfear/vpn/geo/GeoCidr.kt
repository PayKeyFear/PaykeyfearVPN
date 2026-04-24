package com.paykeyfear.vpn.geo

/**
 * Parsed CIDR block: `prefix` is the numeric network (IPv4 or IPv6)
 * as a dotted-quad / colon-hex string and `prefixLength` is the
 * number of significant bits (0..32 for IPv4, 0..128 for IPv6).
 */
data class GeoCidr(
    val address: String,
    val prefixLength: Int,
    val isIpv6: Boolean,
) {
    override fun toString(): String = "$address/$prefixLength"
}

object GeoCidrParser {
    fun parse(text: String): List<GeoCidr> =
        text.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseLine(it) }
            .toList()

    private fun parseLine(line: String): GeoCidr? {
        val slash = line.indexOf('/')
        if (slash < 0) return null
        val addr = line.substring(0, slash)
        val prefix = line.substring(slash + 1).toIntOrNull() ?: return null
        val isIpv6 = ':' in addr
        if (isIpv6) {
            if (prefix !in 0..128) return null
        } else {
            if (prefix !in 0..32) return null
        }
        return GeoCidr(addr, prefix, isIpv6)
    }
}
