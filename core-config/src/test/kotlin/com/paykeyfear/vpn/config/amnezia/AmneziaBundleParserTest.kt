package com.paykeyfear.vpn.config.amnezia

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.core.model.ConnectionConfig
import java.util.Base64
import org.junit.Test

class AmneziaBundleParserTest {
    private val parser = AmneziaBundleParser()

    @Test
    fun `canParse accepts vpn scheme and raw json`() {
        val json = """{"containers":[], "defaultContainer":"awg"}"""
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        assertThat(parser.canParse(ConfigSource.Text("f", "vpn://$b64"))).isTrue()
        assertThat(parser.canParse(ConfigSource.Text("f", json))).isTrue()
    }

    @Test
    fun `parses amnezia bundle containing awg`() {
        val wg = """
            [Interface]
            PrivateKey = aa
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = bb
            AllowedIPs = 0.0.0.0/0
            Endpoint = example.com:51820
        """.trimIndent()
        val bundle = """
            {
              "defaultContainer": "amnezia-awg",
              "description": "Amnezia Bundle",
              "containers": [
                {"container": "amnezia-awg", "last_config": ${escape(wg)}}
              ]
            }
        """.trimIndent()
        val parsed = parser.parse(ConfigSource.Text("bundle", bundle)) as ConnectionConfig.Awg
        assertThat(parsed.privateKey).isEqualTo("aa")
        assertThat(parsed.peerPublicKey).isEqualTo("bb")
        assertThat(parsed.endpoint.port).isEqualTo(51820)
        assertThat(parsed.displayName).isEqualTo("Amnezia Bundle")
    }

    @Test
    fun `parses amnezia bundle containing vless`() {
        val vless = "vless://uuid-123@proxy.example.com:443?encryption=none&security=tls&sni=proxy.example.com#Inner"
        val bundle = """
            {
              "defaultContainer": "amnezia-xray",
              "description": "Xray Amnezia",
              "containers": [
                {"container": "amnezia-xray", "last_config": ${escape(vless)}}
              ]
            }
        """.trimIndent()
        val parsed = parser.parse(ConfigSource.Text("bundle", bundle)) as ConnectionConfig.Vless
        assertThat(parsed.endpoint.host).isEqualTo("proxy.example.com")
        assertThat(parsed.security).isEqualTo("tls")
    }

    @Test
    fun `decodes base64 url bundle`() {
        val inner = """{"defaultContainer":"amnezia-hysteria","description":"X","containers":[{"container":"amnezia-hysteria","last_config":"server: h:443\nauth: pw"}]}"""
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(inner.toByteArray())
        val parsed = parser.parse(ConfigSource.Text("src", "vpn://$encoded")) as ConnectionConfig.Hysteria2
        assertThat(parsed.password).isEqualTo("pw")
        assertThat(parsed.endpoint.host).isEqualTo("h")
    }

    @Test
    fun `accepts bare base64 bundle without vpn scheme`() {
        val inner = """{"defaultContainer":"amnezia-hysteria","description":"X","containers":[{"container":"amnezia-hysteria","last_config":"server: h:443\nauth: pw"}]}"""
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(inner.toByteArray())
        val src = ConfigSource.Text("src", encoded)
        assertThat(parser.canParse(src)).isTrue()
        val parsed = parser.parse(src) as ConnectionConfig.Hysteria2
        assertThat(parsed.password).isEqualTo("pw")
    }

    private fun escape(inner: String): String = "\"${inner.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
}
