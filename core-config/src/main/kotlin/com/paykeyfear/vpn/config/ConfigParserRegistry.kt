package com.paykeyfear.vpn.config

import com.paykeyfear.vpn.config.amnezia.AmneziaBundleParser
import com.paykeyfear.vpn.config.awg.AwgConfParser
import com.paykeyfear.vpn.config.hysteria2.Hysteria2YamlParser
import com.paykeyfear.vpn.config.vless.VlessUriParser
import com.paykeyfear.vpn.core.model.ConnectionConfig

/**
 * Tries every registered parser until one accepts the source.
 *
 * Order matters: higher-priority formats (Amnezia bundles — may contain
 * multiple inner configs) come before single-protocol parsers.
 */
class ConfigParserRegistry(
    private val parsers: List<ConfigParser> =
        listOf(
            AmneziaBundleParser(),
            AwgConfParser(),
            VlessUriParser(),
            Hysteria2YamlParser(),
        ),
) {
    fun parse(source: ConfigSource): ConnectionConfig {
        val candidate = parsers.firstOrNull { it.canParse(source) }
            ?: throw ConfigParseException("No parser recognised config '${source.name}'")
        return candidate.parse(source)
    }
}
