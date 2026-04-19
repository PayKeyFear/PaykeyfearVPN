package com.paykeyfear.vpn.protocols.hysteria2

import com.paykeyfear.vpn.core.model.ConnectionConfig

/**
 * Renders a [ConnectionConfig.Hysteria2] into the YAML accepted by the
 * hysteria client library (github.com/apernet/hysteria).
 */
object Hysteria2ConfigRenderer {
    fun render(config: ConnectionConfig.Hysteria2): String =
        buildString {
            appendLine("server: ${config.endpoint.host}:${config.endpoint.port}")
            appendLine("auth: ${yamlQuote(config.password)}")
            if (config.sni != null || config.insecure || config.pinSha256 != null) {
                appendLine("tls:")
                config.sni?.let { appendLine("  sni: ${yamlQuote(it)}") }
                if (config.insecure) appendLine("  insecure: true")
                config.pinSha256?.let { appendLine("  pinSHA256: ${yamlQuote(it)}") }
            }
            config.obfs?.let {
                appendLine("obfs:")
                appendLine("  type: ${yamlQuote(it.type)}")
                appendLine("  ${it.type}:")
                appendLine("    password: ${yamlQuote(it.password)}")
            }
            config.bandwidth?.let {
                appendLine("bandwidth:")
                it.up?.let { up -> appendLine("  up: ${yamlQuote(up)}") }
                it.down?.let { down -> appendLine("  down: ${yamlQuote(down)}") }
            }
        }

    private fun yamlQuote(value: String): String {
        val needsQuote = value.any { it.isWhitespace() || it in ":#,[]{}&*!|>'\"%@" }
        return if (needsQuote) "\"${value.replace("\"", "\\\"")}\"" else value
    }
}
