package com.paykeyfear.vpn.geo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RouteExclusionTest {
    @Test
    fun `empty ru set returns full ipv4 space`() {
        assertThat(RouteExclusion.complementIpv4(emptyList()))
            .containsExactly(GeoCidr("0.0.0.0", 0, false))
    }

    @Test
    fun `single mid ru range splits into two halves`() {
        val ru = listOf(GeoCidr("10.0.0.0", 8, false))
        val out = RouteExclusion.complementIpv4(ru)
        // Complement is 0.0.0.0..9.255.255.255 + 11.0.0.0..255.255.255.255.
        assertThat(out).contains(GeoCidr("11.0.0.0", 8, false))
        assertThat(out).contains(GeoCidr("12.0.0.0", 6, false))
        // First slice of the low half.
        assertThat(out.first()).isEqualTo(GeoCidr("0.0.0.0", 5, false))
    }

    @Test
    fun `adjacent ru ranges merge before complement`() {
        val ru = listOf(
            GeoCidr("10.0.0.0", 8, false),
            GeoCidr("11.0.0.0", 8, false),
        )
        val out = RouteExclusion.complementIpv4(ru)
        // The result MUST NOT contain 10.0.0.0/8 or 11.0.0.0/8.
        assertThat(out).doesNotContain(GeoCidr("10.0.0.0", 8, false))
        assertThat(out).doesNotContain(GeoCidr("11.0.0.0", 8, false))
        // And MUST contain 12.0.0.0/6 (the next-up aligned block).
        assertThat(out).contains(GeoCidr("12.0.0.0", 6, false))
    }

    @Test
    fun `complement exactly tiles address space`() {
        val ru = listOf(GeoCidr("10.0.0.0", 8, false))
        val out = RouteExclusion.complementIpv4(ru)
        val totalAddresses = out.sumOf { 1L shl (32 - it.prefixLength) }
        val ruAddresses = 1L shl (32 - 8)
        assertThat(totalAddresses + ruAddresses).isEqualTo(1L shl 32)
    }

    @Test
    fun `ipv6 empty set returns default route`() {
        assertThat(RouteExclusion.complementIpv6(emptyList()))
            .containsExactly(GeoCidr("::", 0, true))
    }
}
