package com.paykeyfear.vpn.config.awg

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import org.junit.Test

class AwgConfParserTest {
    private val parser = AwgConfParser()

    @Test
    fun `canParse accepts wg config with interface and peer sections`() {
        val src = ConfigSource.Text("t", sampleFixture())
        assertThat(parser.canParse(src)).isTrue()
    }

    @Test
    fun `canParse rejects random text`() {
        val src = ConfigSource.Text("t", "vless://foo@bar:443")
        assertThat(parser.canParse(src)).isFalse()
    }

    @Test
    fun `parse extracts interface, peer and junk params`() {
        val parsed = parser.parse(ConfigSource.Text("Germany", sampleFixture())) as ConnectionConfig.Awg
        assertThat(parsed.protocol).isEqualTo(Protocol.AWG)
        assertThat(parsed.displayName).isEqualTo("Germany")
        assertThat(parsed.privateKey).isEqualTo("cIIHgBCa0q6t7aCHXWdMUjIyR4t+V4tk4lOtItKtlGM=")
        assertThat(parsed.peerPublicKey).isEqualTo("qLK4w5KDxJKWxzQjMDhR9Xp3ylCj6rTUtZnEHrqkiX0=")
        assertThat(parsed.presharedKey).isEqualTo("hZ3j9Pq/m+0tPcGxGm7e2kHpB6o0l3fZV4+rYv6iszY=")
        assertThat(parsed.addresses).containsExactly("10.66.66.2/32", "fd42:42:42::2/128")
        assertThat(parsed.dns).containsExactly("1.1.1.1", "8.8.8.8")
        assertThat(parsed.endpoint.host).isEqualTo("198.51.100.7")
        assertThat(parsed.endpoint.port).isEqualTo(51820)
        assertThat(parsed.allowedIps).containsExactly("0.0.0.0/0", "::/0")
        assertThat(parsed.mtu).isEqualTo(1380)
        assertThat(parsed.persistentKeepalive).isEqualTo(25)
        assertThat(parsed.junk.jc).isEqualTo(4)
        assertThat(parsed.junk.h4).isEqualTo(4_234_567_890L)
    }

    @Test
    fun `parse ignores inline comments and blank lines`() {
        val text = """
            # top level comment

            [Interface]
            PrivateKey = aaa # inline
            Address = 10.0.0.1/32

            [Peer]
            PublicKey = bbb
            AllowedIPs = 0.0.0.0/0
            Endpoint = 1.2.3.4:51820
        """.trimIndent()
        val parsed = parser.parse(ConfigSource.Text("c", text)) as ConnectionConfig.Awg
        assertThat(parsed.privateKey).isEqualTo("aaa")
        assertThat(parsed.peerPublicKey).isEqualTo("bbb")
    }

    @Test(expected = Exception::class)
    fun `parse throws when endpoint is missing`() {
        val text = """
            [Interface]
            PrivateKey = a
            [Peer]
            PublicKey = b
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        parser.parse(ConfigSource.Text("c", text))
    }

    private fun sampleFixture(): String =
        javaClass.classLoader!!.getResourceAsStream("configs/awg/sample.conf")!!.bufferedReader().readText()
}
