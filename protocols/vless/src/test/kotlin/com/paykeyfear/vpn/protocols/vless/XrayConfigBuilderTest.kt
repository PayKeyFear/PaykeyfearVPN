package com.paykeyfear.vpn.protocols.vless

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class XrayConfigBuilderTest {
    @Test
    fun `builds reality outbound with full streamSettings`() {
        val cfg = ConnectionConfig.Vless(
            id = "i",
            displayName = "n",
            endpoint = Endpoint("edge", 443),
            userId = "uuid-1",
            flow = "xtls-rprx-vision",
            security = "reality",
            sni = "www.cloudflare.com",
            publicKey = "PUB",
            shortId = "SID",
            fingerprint = "chrome",
            network = "tcp",
        )
        val root = XrayConfigBuilder.build(cfg, socksPort = 10808)
        val outbounds = root["outbounds"] as JsonArray
        val proxy = outbounds[0] as JsonObject
        val stream = proxy["streamSettings"] as JsonObject
        assertThat(stream["security"]?.jsonPrimitive?.content).isEqualTo("reality")
        val reality = stream["realitySettings"] as JsonObject
        assertThat(reality["serverName"]?.jsonPrimitive?.content).isEqualTo("www.cloudflare.com")
        assertThat(reality["publicKey"]?.jsonPrimitive?.content).isEqualTo("PUB")
        assertThat(reality["shortId"]?.jsonPrimitive?.content).isEqualTo("SID")
    }

    @Test
    fun `websocket network emits wsSettings with path`() {
        val cfg = ConnectionConfig.Vless(
            id = "i",
            displayName = "n",
            endpoint = Endpoint("edge", 443),
            userId = "uuid-1",
            network = "ws",
            path = "/ws",
        )
        val root = XrayConfigBuilder.build(cfg, socksPort = 10808)
        val stream = ((root["outbounds"] as JsonArray)[0] as JsonObject)["streamSettings"] as JsonObject
        val ws = stream["wsSettings"] as JsonObject
        assertThat(ws["path"]?.jsonPrimitive?.content).isEqualTo("/ws")
    }

    @Test
    fun `grpc network emits grpcSettings with serviceName`() {
        val cfg = ConnectionConfig.Vless(
            id = "i",
            displayName = "n",
            endpoint = Endpoint("edge", 443),
            userId = "uuid-1",
            network = "grpc",
            serviceName = "xyz",
        )
        val root = XrayConfigBuilder.build(cfg, socksPort = 10808)
        val stream = ((root["outbounds"] as JsonArray)[0] as JsonObject)["streamSettings"] as JsonObject
        val grpc = stream["grpcSettings"] as JsonObject
        assertThat(grpc["serviceName"]?.jsonPrimitive?.content).isEqualTo("xyz")
    }

    @Test
    fun `users array contains uuid with encryption none`() {
        val cfg = ConnectionConfig.Vless(
            id = "i",
            displayName = "n",
            endpoint = Endpoint("edge", 443),
            userId = "uuid-1",
        )
        val root = XrayConfigBuilder.build(cfg, socksPort = 10808)
        val vnext = (((root["outbounds"] as JsonArray)[0] as JsonObject)["settings"] as JsonObject)["vnext"] as JsonArray
        val user = ((vnext[0] as JsonObject)["users"] as JsonArray)[0] as JsonObject
        assertThat(user["id"]?.jsonPrimitive?.content).isEqualTo("uuid-1")
        assertThat(user["encryption"]?.jsonPrimitive?.content).isEqualTo("none")
    }
}
