package com.paykeyfear.vpn.protocols.awg

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.model.AwgJunkParams
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AwgConfigRendererTest {
    // 32-byte base64 keys (all-zero / all-one / all-two padding) for round-trip testing.
    private val privB64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val pubB64 = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    private val pskB64 = "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="

    private val privHex = "0".repeat(64)
    private val pubHex = "01".repeat(32)
    private val pskHex = "02".repeat(32)

    @Test
    fun `render emits full IPC block in expected order`() {
        val cfg = ConnectionConfig.Awg(
            id = "id",
            displayName = "name",
            endpoint = Endpoint("198.51.100.7", 51820),
            privateKey = privB64,
            peerPublicKey = pubB64,
            presharedKey = pskB64,
            addresses = listOf("10.0.0.2/32"),
            mtu = 1420,
            persistentKeepalive = 25,
            junk = AwgJunkParams(jc = 4, jmin = 40, jmax = 70, s1 = 50, s2 = 100, h1 = "1", h2 = "2", h3 = "3", h4 = "4"),
            allowedIps = listOf("0.0.0.0/0", "::/0"),
        )
        val out = AwgConfigRenderer.render(cfg)
        assertThat(out).contains("private_key=$privHex")
        assertThat(out).contains("public_key=$pubHex")
        assertThat(out).contains("preshared_key=$pskHex")
        assertThat(out).contains("endpoint=198.51.100.7:51820")
        assertThat(out).contains("persistent_keepalive_interval=25")
        // MTU is applied via VpnService.Builder.setMtu, not via IpcSet
        assertThat(out).doesNotContain("mtu=")
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
            privateKey = privB64,
            peerPublicKey = pubB64,
            addresses = listOf("10.0.0.2/32"),
        )
        val out = AwgConfigRenderer.render(cfg)
        assertThat(out).doesNotContain("preshared_key=")
        assertThat(out).doesNotContain("mtu=")
        assertThat(out).doesNotContain("jc=")
    }

    @Test
    fun `toHexKey accepts hex passthrough`() {
        assertThat(AwgConfigRenderer.toHexKey(pubHex)).isEqualTo(pubHex)
        assertThat(AwgConfigRenderer.toHexKey(pubHex.uppercase())).isEqualTo(pubHex)
    }
}
