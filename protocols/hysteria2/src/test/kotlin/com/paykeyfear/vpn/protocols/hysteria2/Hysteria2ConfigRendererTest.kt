package com.paykeyfear.vpn.protocols.hysteria2

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.model.BandwidthConfig
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint
import com.paykeyfear.vpn.core.model.ObfsConfig
import org.junit.Test

class Hysteria2ConfigRendererTest {
    @Test
    fun `renders minimal yaml with server and auth`() {
        val yaml = Hysteria2ConfigRenderer.render(
            ConnectionConfig.Hysteria2(
                id = "i",
                displayName = "n",
                endpoint = Endpoint("edge", 8443),
                password = "simple",
            ),
        )
        assertThat(yaml).contains("server: edge:8443")
        assertThat(yaml).contains("auth: simple")
        assertThat(yaml).doesNotContain("tls:")
        assertThat(yaml).doesNotContain("obfs:")
    }

    @Test
    fun `quotes password with special chars and includes tls block`() {
        val yaml = Hysteria2ConfigRenderer.render(
            ConnectionConfig.Hysteria2(
                id = "i",
                displayName = "n",
                endpoint = Endpoint("edge", 8443),
                password = "with spaces: yes!",
                sni = "edge.example",
                insecure = true,
            ),
        )
        assertThat(yaml).contains("auth: \"with spaces: yes!\"")
        assertThat(yaml).contains("tls:")
        assertThat(yaml).contains("  sni: edge.example")
        assertThat(yaml).contains("  insecure: true")
    }

    @Test
    fun `renders obfs and bandwidth blocks`() {
        val yaml = Hysteria2ConfigRenderer.render(
            ConnectionConfig.Hysteria2(
                id = "i",
                displayName = "n",
                endpoint = Endpoint("edge", 8443),
                password = "p",
                obfs = ObfsConfig("salamander", "saltyPW"),
                bandwidth = BandwidthConfig(up = "10 mbps", down = "100 mbps"),
            ),
        )
        assertThat(yaml).contains("obfs:")
        assertThat(yaml).contains("  type: salamander")
        assertThat(yaml).contains("  salamander:")
        assertThat(yaml).contains("    password: saltyPW")
        assertThat(yaml).contains("bandwidth:")
        assertThat(yaml).contains("  up: \"10 mbps\"")
    }
}
