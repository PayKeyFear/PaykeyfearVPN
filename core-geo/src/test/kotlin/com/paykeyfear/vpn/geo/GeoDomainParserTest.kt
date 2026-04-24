package com.paykeyfear.vpn.geo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoDomainParserTest {
    @Test
    fun `classifies each line kind`() {
        val parsed = GeoDomainParser.parse(
            """
            # header
            example.com
            full:exact.example.com
            include:tld-ru
            keyword:promo
            regexp:^abc\.ru$
            """.trimIndent(),
        )
        assertThat(parsed).containsExactly(
            GeoDomainParser.Entry(GeoDomainParser.Entry.Kind.Domain, "example.com"),
            GeoDomainParser.Entry(GeoDomainParser.Entry.Kind.Full, "exact.example.com"),
            GeoDomainParser.Entry(GeoDomainParser.Entry.Kind.Include, "tld-ru"),
            GeoDomainParser.Entry(GeoDomainParser.Entry.Kind.Keyword, "promo"),
            GeoDomainParser.Entry(GeoDomainParser.Entry.Kind.Regexp, "^abc\\.ru$"),
        )
    }

    @Test
    fun `strips attribute tags`() {
        val parsed = GeoDomainParser.parse("example.com @ads")
        assertThat(parsed).containsExactly(
            GeoDomainParser.Entry(GeoDomainParser.Entry.Kind.Domain, "example.com"),
        )
    }
}
