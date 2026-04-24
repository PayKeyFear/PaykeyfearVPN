package com.paykeyfear.vpn.protocols.vless

import com.paykeyfear.vpn.core.model.ConnectionConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Produces a minimal Xray-core JSON config for a VLESS outbound.
 *
 * The inbound is a SOCKS5 listener on `127.0.0.1:[socksPort]` — a local
 * tun2socks bridge (see [VlessTunnel.Tun2SocksBridge]) forwards packets
 * from the Android TUN fd into this SOCKS port. Xray never sees the TUN
 * fd itself — that architecture is documented in
 * [docs/VLESS_INTEGRATION.md](../../../../../../../../docs/VLESS_INTEGRATION.md).
 *
 * Covers plain VLESS, VLESS over TLS, and VLESS-Reality. WebSocket/GRPC
 * transport is respected via `network` / `serviceName` / `path`.
 */
object XrayConfigBuilder {
    fun build(config: ConnectionConfig.Vless, socksPort: Int): JsonObject =
        buildJsonObject {
            put("log", buildJsonObject { put("loglevel", "warning") })
            put("inbounds", inbounds(socksPort))
            put("outbounds", outbounds(config))
        }

    private fun inbounds(socksPort: Int): JsonArray =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("tag", "socks-in")
                    put("protocol", "socks")
                    put("listen", "127.0.0.1")
                    put("port", socksPort)
                    put(
                        "settings",
                        buildJsonObject {
                            put("auth", "noauth")
                            // UDP intentionally off: xjasonlyu/tun2socks v2.6.0
                            // panics with "slice bounds out of range [:N] cap 2048"
                            // on QUIC jumbo frames from gvisor. Browsers fall back
                            // from HTTP/3 to HTTP/2 TCP cleanly.
                            put("udp", false)
                        },
                    )
                    put(
                        "sniffing",
                        buildJsonObject {
                            put("enabled", true)
                            put(
                                "destOverride",
                                buildJsonArray {
                                    add(kotlinx.serialization.json.JsonPrimitive("http"))
                                    add(kotlinx.serialization.json.JsonPrimitive("tls"))
                                },
                            )
                        },
                    )
                },
            )
        }

    private fun outbounds(config: ConnectionConfig.Vless): JsonArray =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("tag", "proxy")
                    put("protocol", "vless")
                    put(
                        "settings",
                        buildJsonObject {
                            put(
                                "vnext",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("address", config.endpoint.host)
                                            put("port", config.endpoint.port)
                                            put(
                                                "users",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("id", config.userId)
                                                            put("encryption", config.encryption)
                                                            config.flow?.let { put("flow", it) }
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                    put("streamSettings", streamSettings(config))
                },
            )
            add(buildJsonObject { put("tag", "direct"); put("protocol", "freedom") })
        }

    private fun streamSettings(config: ConnectionConfig.Vless): JsonObject =
        buildJsonObject {
            put("network", config.network)
            config.security?.takeIf { it.isNotBlank() }?.let { put("security", it) }
            if (config.security == "reality") {
                put(
                    "realitySettings",
                    buildJsonObject {
                        config.sni?.let { put("serverName", it) }
                        config.publicKey?.let { put("publicKey", it) }
                        config.shortId?.let { put("shortId", it) }
                        config.fingerprint?.let { put("fingerprint", it) }
                    },
                )
            } else if (config.security == "tls") {
                put(
                    "tlsSettings",
                    buildJsonObject {
                        config.sni?.let { put("serverName", it) }
                        config.fingerprint?.let { put("fingerprint", it) }
                    },
                )
            }
            when (config.network) {
                "ws" -> put(
                    "wsSettings",
                    buildJsonObject { config.path?.let { put("path", it) } },
                )
                "grpc" -> put(
                    "grpcSettings",
                    buildJsonObject { config.serviceName?.let { put("serviceName", it) } },
                )
            }
        }
}
