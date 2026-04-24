package com.paykeyfear.vpn.geo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoCidrParserTest {
    @Test
    fun `parses ipv4 cidr with comment and blanks`() {
        val parsed = GeoCidrParser.parse(
            """
            # header
            1.2.3.0/24

              10.0.0.0/8  # private
            """.trimIndent(),
        )
        assertThat(parsed).containsExactly(
            GeoCidr("1.2.3.0", 24, false),
            GeoCidr("10.0.0.0", 8, false),
        )
    }

    @Test
    fun `parses ipv6 cidr`() {
        val parsed = GeoCidrParser.parse("2a02:6b8::/32")
        assertThat(parsed).containsExactly(GeoCidr("2a02:6b8::", 32, true))
    }

    @Test
    fun `rejects malformed lines`() {
        val parsed = GeoCidrParser.parse(
            """
            not-a-cidr
            10.0.0.0/99
            10.0.0.0
            """.trimIndent(),
        )
        assertThat(parsed).isEmpty()
    }
}
