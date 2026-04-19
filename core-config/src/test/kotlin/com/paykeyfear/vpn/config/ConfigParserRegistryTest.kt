package com.paykeyfear.vpn.config

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.model.ConnectionConfig
import org.junit.Test

class ConfigParserRegistryTest {
    private val registry = ConfigParserRegistry()

    @Test
    fun `routes vless uri to vless parser`() {
        val cfg = registry.parse(ConfigSource.Text("f", "vless://id@h.example:443?encryption=none#l"))
        assertThat(cfg).isInstanceOf(ConnectionConfig.Vless::class.java)
    }

    @Test
    fun `routes hysteria2 uri to hysteria parser`() {
        val cfg = registry.parse(ConfigSource.Text("f", "hysteria2://pw@h.example:443?insecure=1"))
        assertThat(cfg).isInstanceOf(ConnectionConfig.Hysteria2::class.java)
    }

    @Test
    fun `routes wg conf to awg parser`() {
        val text = """
            [Interface]
            PrivateKey = aa
            [Peer]
            PublicKey = bb
            AllowedIPs = 0.0.0.0/0
            Endpoint = h:51820
        """.trimIndent()
        val cfg = registry.parse(ConfigSource.Text("f", text))
        assertThat(cfg).isInstanceOf(ConnectionConfig.Awg::class.java)
    }

    @Test(expected = ConfigParseException::class)
    fun `throws for unknown format`() {
        registry.parse(ConfigSource.Text("f", "something totally unrelated"))
    }
}
