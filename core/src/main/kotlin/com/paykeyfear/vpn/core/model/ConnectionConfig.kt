package com.paykeyfear.vpn.core.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface ConnectionConfig {
    val id: String
    val displayName: String
    val protocol: Protocol
    val endpoint: Endpoint

    @Serializable
    data class Awg(
        override val id: String,
        override val displayName: String,
        override val endpoint: Endpoint,
        val privateKey: String,
        val peerPublicKey: String,
        val presharedKey: String? = null,
        val addresses: List<String>,
        val dns: List<String> = emptyList(),
        val allowedIps: List<String> = listOf("0.0.0.0/0", "::/0"),
        val mtu: Int? = null,
        val persistentKeepalive: Int? = null,
        val junk: AwgJunkParams = AwgJunkParams(),
    ) : ConnectionConfig {
        override val protocol: Protocol = Protocol.AWG
    }

    @Serializable
    data class Vless(
        override val id: String,
        override val displayName: String,
        override val endpoint: Endpoint,
        val userId: String,
        val flow: String? = null,
        val encryption: String = "none",
        val network: String = "tcp",
        val security: String? = null,
        val sni: String? = null,
        val publicKey: String? = null,
        val shortId: String? = null,
        val fingerprint: String? = null,
        val path: String? = null,
        val serviceName: String? = null,
    ) : ConnectionConfig {
        override val protocol: Protocol = Protocol.VLESS
    }

    @Serializable
    data class Hysteria2(
        override val id: String,
        override val displayName: String,
        override val endpoint: Endpoint,
        val password: String,
        val sni: String? = null,
        val insecure: Boolean = false,
        val obfs: ObfsConfig? = null,
        val bandwidth: BandwidthConfig? = null,
        val pinSha256: String? = null,
    ) : ConnectionConfig {
        override val protocol: Protocol = Protocol.HYSTERIA2
    }
}

@Serializable
data class AwgJunkParams(
    val jc: Int? = null,
    val jmin: Int? = null,
    val jmax: Int? = null,
    val s1: Int? = null,
    val s2: Int? = null,
    val h1: Long? = null,
    val h2: Long? = null,
    val h3: Long? = null,
    val h4: Long? = null,
)

@Serializable
data class ObfsConfig(
    val type: String,
    val password: String,
)

@Serializable
data class BandwidthConfig(
    val up: String? = null,
    val down: String? = null,
)
