package com.paykeyfear.vpn.protocols.awg

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.model.AwgJunkParams
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint
import org.junit.Test

class AwgConfigRendererTest {
    @Test
    fun `render emits full IPC block in expected order`() {
        val cfg = ConnectionConfig.Awg(
            id = "id",
            displayName = "name",
            endpoint = Endpoint("198.51.100.7", 51820),
            privateKey = "PRIV",
            peerPublicKey = "PUB",
            presharedKey = "PSK",
            addresses = listOf("10.0.0.2/32"),
            mtu = 1420,
            persistentKeepalive = 25,
            junk = AwgJunkParams(jc = 4, jmin = 40, jmax = 70, s1 = 50, s2 = 100, h1 = 1, h2 = 2, h3 = 3, h4 = 4),
            allowedIps = listOf("0.0.0.0/0", "::/0"),
        )
        val out = AwgConfigRenderer.render(cfg)
        assertThat(out).contains("private_key=PRIV")
        assertThat(out).contains("public_key=PUB")
        assertThat(out).contains("preshared_key=PSK")
        assertThat(out).contains("endpoint=198.51.100.7:51820")
        assertThat(out).contains("persistent_keepalive_interval=25")
        assertThat(out).contains("mtu=1420")
        assertThat(out).contains("jc=4")
        assertThat(out).contains("h4=4")
        assertThat(out).contains("allowed_ip=0.0.0.0/0")
        assertThat(out).contains("allowed_ip=::/0")
    }

    @Test
    fun `render skips optional fields when null`() {
        val cfg = ConnectionConfig.Awg(
            id = "id",
            displayName = "name",
            endpoint = Endpoint("h", 51820),
            privateKey = "PRIV",
            peerPublicKey = "PUB",
            addresses = listOf("10.0.0.2/32"),
        )
        val out = AwgConfigRenderer.render(cfg)
        assertThat(out).doesNotContain("preshared_key=")
        assertThat(out).doesNotContain("mtu=")
        assertThat(out).doesNotContain("jc=")
    }
}
