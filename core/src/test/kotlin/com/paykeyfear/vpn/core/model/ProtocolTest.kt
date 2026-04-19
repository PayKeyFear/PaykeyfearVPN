package com.paykeyfear.vpn.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProtocolTest {
    @Test
    fun `fromId resolves case-insensitively`() {
        assertThat(Protocol.fromId("awg")).isEqualTo(Protocol.AWG)
        assertThat(Protocol.fromId("VLESS")).isEqualTo(Protocol.VLESS)
        assertThat(Protocol.fromId("Hysteria2")).isEqualTo(Protocol.HYSTERIA2)
    }

    @Test
    fun `fromId returns null for unknown id`() {
        assertThat(Protocol.fromId("openvpn")).isNull()
    }
}
