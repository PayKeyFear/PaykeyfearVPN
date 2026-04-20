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
    // s3 = cookie-reply padding, s4 = transport padding. Both added by
    // AmneziaWG 1.5 and accepted by amneziawg-go v0.2.17's UAPI.
    val s3: Int? = null,
    val s4: Int? = null,
    // Magic-header specs. In AmneziaWG 1.5 configs they come as ranges
    // ("743502058-1997075986"); in AWG 2.0 configs they are single uint32
    // values. Stored as raw strings so the renderer can decide how to
    // materialize them for the current amneziawg-go backend.
    val h1: String? = null,
    val h2: String? = null,
    val h3: String? = null,
    val h4: String? = null,
    // AmneziaWG 2.0 "special" handshake junk expressions. Their grammar
    // (e.g. "<b 0xAA><c 100><t>") is parsed server-side by amneziawg-go's
    // awg.Parse; clients just pass them through verbatim.
    val i1: String? = null,
    val i2: String? = null,
    val i3: String? = null,
    val i4: String? = null,
    val i5: String? = null,
    val j1: String? = null,
    val j2: String? = null,
    val j3: String? = null,
    val itime: Int? = null,
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
