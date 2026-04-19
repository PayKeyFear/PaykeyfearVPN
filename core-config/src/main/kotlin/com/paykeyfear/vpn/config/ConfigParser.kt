package com.paykeyfear.vpn.config

import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol

/**
 * Parses a text/binary config for a single protocol into a [ConnectionConfig].
 */
interface ConfigParser {
    val protocol: Protocol

    fun canParse(source: ConfigSource): Boolean

    fun parse(source: ConfigSource): ConnectionConfig
}

class ConfigParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
