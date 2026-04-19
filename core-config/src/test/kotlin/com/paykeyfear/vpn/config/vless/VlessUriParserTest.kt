package com.paykeyfear.vpn.config.vless

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import org.junit.Test

class VlessUriParserTest {
    private val parser = VlessUriParser()

    @Test
    fun `canParse accepts vless scheme`() {
        assertThat(parser.canParse(ConfigSource.Text("t", "vless://uuid@host:443"))).isTrue()
    }

    @Test
    fun `canParse rejects other schemes`() {
        assertThat(parser.canParse(ConfigSource.Text("t", "hysteria2://pw@host:443"))).isFalse()
    }

    @Test
    fun `parses reality vless with full params`() {
        val uri =
            "vless://550e8400-e29b-41d4-a716-446655440000@gateway.example.com:443" +
                "?encryption=none&flow=xtls-rprx-vision&security=reality" +
                "&sni=www.cloudflare.com&fp=chrome&pbk=Kpublic123&sid=abcd" +
                "&type=tcp#MyLabel"
        val parsed = parser.parse(ConfigSource.Text("fallback", uri)) as ConnectionConfig.Vless
        assertThat(parsed.protocol).isEqualTo(Protocol.VLESS)
        assertThat(parsed.displayName).isEqualTo("MyLabel")
        assertThat(parsed.userId).isEqualTo("550e8400-e29b-41d4-a716-446655440000")
        assertThat(parsed.endpoint.host).isEqualTo("gateway.example.com")
        assertThat(parsed.endpoint.port).isEqualTo(443)
        assertThat(parsed.security).isEqualTo("reality")
        assertThat(parsed.sni).isEqualTo("www.cloudflare.com")
        assertThat(parsed.flow).isEqualTo("xtls-rprx-vision")
        assertThat(parsed.publicKey).isEqualTo("Kpublic123")
        assertThat(parsed.shortId).isEqualTo("abcd")
        assertThat(parsed.fingerprint).isEqualTo("chrome")
    }

    @Test
    fun `uses source name when fragment is absent`() {
        val uri = "vless://id@example.com:443?encryption=none"
        val parsed = parser.parse(ConfigSource.Text("srcname", uri)) as ConnectionConfig.Vless
        assertThat(parsed.displayName).isEqualTo("srcname")
    }

    @Test
    fun `decodes url-encoded fragment label`() {
        val uri = "vless://id@example.com:443?encryption=none#My%20Server%20%F0%9F%94%92"
        val parsed = parser.parse(ConfigSource.Text("f", uri)) as ConnectionConfig.Vless
        assertThat(parsed.displayName).isEqualTo("My Server \uD83D\uDD12")
    }
}
