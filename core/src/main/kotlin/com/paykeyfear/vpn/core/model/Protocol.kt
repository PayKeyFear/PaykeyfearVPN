package com.paykeyfear.vpn.core.model

enum class Protocol(val displayName: String, val defaultPort: Int) {
    AWG("AmneziaWG 2.0", 51820),
    VLESS("VLESS", 443),
    HYSTERIA2("Hysteria2", 443),
    ;

    companion object {
        fun fromId(id: String): Protocol? = entries.firstOrNull { it.name.equals(id, ignoreCase = true) }
    }
}
