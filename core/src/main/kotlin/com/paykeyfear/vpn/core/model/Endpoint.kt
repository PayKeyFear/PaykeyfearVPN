package com.paykeyfear.vpn.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Endpoint(
    val host: String,
    val port: Int,
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..MAX_PORT) { "port $port is outside 1..$MAX_PORT" }
    }

    override fun toString(): String = "$host:$port"

    companion object {
        const val MAX_PORT: Int = 65_535

        fun parse(value: String): Endpoint {
            val trimmed = value.trim()
            val ipv6 = trimmed.startsWith("[")
            val hostPort =
                if (ipv6) {
                    val end = trimmed.indexOf(']')
                    require(end > 0 && end + 2 < trimmed.length && trimmed[end + 1] == ':') {
                        "Invalid IPv6 endpoint: $value"
                    }
                    trimmed.substring(1, end) to trimmed.substring(end + 2).toInt()
                } else {
                    val parts = trimmed.split(":")
                    require(parts.size == 2) { "Invalid endpoint (expected host:port): $value" }
                    parts[0] to parts[1].toInt()
                }
            return Endpoint(hostPort.first, hostPort.second)
        }
    }
}
