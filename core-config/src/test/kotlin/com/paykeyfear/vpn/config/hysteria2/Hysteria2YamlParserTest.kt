package com.paykeyfear.vpn.config.hysteria2

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import org.junit.Test

class Hysteria2YamlParserTest {
    private val parser = Hysteria2YamlParser()

    @Test
    fun `canParse accepts hysteria2 uri`() {
        assertThat(parser.canParse(ConfigSource.Text("t", "hysteria2://pw@host:443?insecure=1"))).isTrue()
    }

    @Test
    fun `canParse accepts yaml with server and auth`() {
        val yaml = "server: host.example.com:443\nauth: secret"
        assertThat(parser.canParse(ConfigSource.Text("t", yaml))).isTrue()
    }

    @Test
    fun `parses hysteria2 uri with obfs and insecure`() {
        val uri =
            "hysteria2://mySecret!@edge.example.net:8443" +
                "?sni=edge.example.net&insecure=1&obfs=salamander&obfs-password=saltyPW&pinSHA256=abc123#Edge"
        val parsed = parser.parse(ConfigSource.Text("f", uri)) as ConnectionConfig.Hysteria2
        assertThat(parsed.protocol).isEqualTo(Protocol.HYSTERIA2)
        assertThat(parsed.password).isEqualTo("mySecret!")
        assertThat(parsed.endpoint.host).isEqualTo("edge.example.net")
        assertThat(parsed.endpoint.port).isEqualTo(8443)
        assertThat(parsed.sni).isEqualTo("edge.example.net")
        assertThat(parsed.insecure).isTrue()
        assertThat(parsed.displayName).isEqualTo("Edge")
        assertThat(parsed.pinSha256).isEqualTo("abc123")
        assertThat(parsed.obfs?.type).isEqualTo("salamander")
        assertThat(parsed.obfs?.password).isEqualTo("saltyPW")
    }

    @Test
    fun `parses nested yaml config`() {
        val yaml = """
            server: host.example.com:443
            auth: secret-pw
            tls:
              sni: host.example.com
              insecure: true
            obfs:
              type: salamander
              salamander:
                password: obfsPW
            bandwidth:
              up: 10 mbps
              down: 100 mbps
        """.trimIndent()
        val parsed = parser.parse(ConfigSource.Text("srv", yaml)) as ConnectionConfig.Hysteria2
        assertThat(parsed.password).isEqualTo("secret-pw")
        assertThat(parsed.sni).isEqualTo("host.example.com")
        assertThat(parsed.insecure).isTrue()
        assertThat(parsed.obfs?.type).isEqualTo("salamander")
        assertThat(parsed.obfs?.password).isEqualTo("obfsPW")
        assertThat(parsed.bandwidth?.up).isEqualTo("10 mbps")
        assertThat(parsed.bandwidth?.down).isEqualTo("100 mbps")
    }
}
