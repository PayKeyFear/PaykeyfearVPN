package com.paykeyfear.vpn.geo

/**
 * Parses v2fly/domain-list-community text files.
 * Format per line:
 *   example.com              # plain domain (suffix match)
 *   keyword:foo              # substring match (ignored here — xray-only)
 *   regexp:^abc              # regex match (ignored here — xray-only)
 *   full:exact.example.com   # exact match
 *   include:category-ru      # pulls another list (recursive, unresolved here)
 * Comments start with `#`. We extract domains + `full:` + keep `include:`
 * markers as-is so Xray's geosite loader can do its own resolution; a
 * future commit may flatten includes at parse time.
 */
object GeoDomainParser {
    data class Entry(
        val kind: Kind,
        val value: String,
    ) {
        enum class Kind { Domain, Full, Include, Keyword, Regexp }
    }

    fun parse(text: String): List<Entry> =
        text.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseLine(it) }
            .toList()

    private fun parseLine(line: String): Entry? {
        val value = line.substringBefore('@').trim()
        if (value.isEmpty()) return null
        val colon = value.indexOf(':')
        if (colon < 0) return Entry(Entry.Kind.Domain, value)
        val type = value.substring(0, colon)
        val rest = value.substring(colon + 1).trim()
        if (rest.isEmpty()) return null
        return when (type) {
            "full" -> Entry(Entry.Kind.Full, rest)
            "include" -> Entry(Entry.Kind.Include, rest)
            "keyword" -> Entry(Entry.Kind.Keyword, rest)
            "regexp" -> Entry(Entry.Kind.Regexp, rest)
            else -> null
        }
    }
}
