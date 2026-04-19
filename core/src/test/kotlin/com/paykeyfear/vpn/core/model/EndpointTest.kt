package com.paykeyfear.vpn.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EndpointTest {
    @Test
    fun `parses simple host and port`() {
        val ep = Endpoint.parse("example.com:51820")
        assertThat(ep.host).isEqualTo("example.com")
        assertThat(ep.port).isEqualTo(51820)
    }

    @Test
    fun `parses ipv6 bracketed endpoint`() {
        val ep = Endpoint.parse("[2001:db8::1]:443")
        assertThat(ep.host).isEqualTo("2001:db8::1")
        assertThat(ep.port).isEqualTo(443)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects missing port`() {
        Endpoint.parse("example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects out-of-range port`() {
        Endpoint("example.com", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects blank host`() {
        Endpoint("", 443)
    }

    @Test
    fun `toString matches canonical host colon port`() {
        assertThat(Endpoint("example.com", 51820).toString()).isEqualTo("example.com:51820")
    }
}
