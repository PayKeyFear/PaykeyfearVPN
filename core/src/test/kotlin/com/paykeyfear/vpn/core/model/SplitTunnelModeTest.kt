package com.paykeyfear.vpn.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SplitTunnelModeTest {
    @Test
    fun `null and unknown values fall back to Off`() {
        assertThat(SplitTunnelMode.fromStorageValue(null)).isEqualTo(SplitTunnelMode.Off)
        assertThat(SplitTunnelMode.fromStorageValue("")).isEqualTo(SplitTunnelMode.Off)
        assertThat(SplitTunnelMode.fromStorageValue("garbage")).isEqualTo(SplitTunnelMode.Off)
    }

    @Test
    fun `known names are parsed case-insensitively`() {
        assertThat(SplitTunnelMode.fromStorageValue("allowlist")).isEqualTo(SplitTunnelMode.Allowlist)
        assertThat(SplitTunnelMode.fromStorageValue("DENYLIST")).isEqualTo(SplitTunnelMode.Denylist)
        assertThat(SplitTunnelMode.fromStorageValue("Off")).isEqualTo(SplitTunnelMode.Off)
    }
}
