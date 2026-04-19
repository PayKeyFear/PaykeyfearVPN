package com.paykeyfear.vpn.config.hysteria2

/**
 * Minimal YAML subset reader (scalars + nested maps by indentation).
 * Intentionally limited to what Hysteria2 configs use — no flow style,
 * no anchors, no lists of maps. Strings may be quoted with `"` or `'`.
 */
internal class SimpleYaml private constructor(
    private val children: Map<String, Node>,
) {
    sealed interface Node {
        data class Scalar(val value: String) : Node

        data class Map(val children: kotlin.collections.Map<String, Node>) : Node
    }

    fun string(key: String): String? = (children[key] as? Node.Scalar)?.value

    fun bool(key: String): Boolean? =
        string(key)?.lowercase()?.let { v ->
            when (v) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
        }

    fun child(key: String): SimpleYaml? = (children[key] as? Node.Map)?.let { SimpleYaml(it.children) }

    companion object {
        fun parse(text: String): SimpleYaml {
            val lines = text.lineSequence()
                .map { it.substringBefore('#').trimEnd() }
                .filter { it.isNotBlank() }
                .toList()
            val (root, _) = readBlock(lines, 0, 0)
            return SimpleYaml(root)
        }

        private fun readBlock(
            lines: List<String>,
            startIndex: Int,
            indent: Int,
        ): Pair<Map<String, Node>, Int> {
            val out = LinkedHashMap<String, Node>()
            var i = startIndex
            while (i < lines.size) {
                val raw = lines[i]
                val lineIndent = raw.takeWhile { it == ' ' }.length
                if (lineIndent < indent) return out to i
                if (lineIndent > indent) error("Unexpected indent at: $raw")
                val content = raw.substring(lineIndent)
                val colon = content.indexOf(':')
                require(colon > 0) { "Malformed YAML line: $raw" }
                val key = content.substring(0, colon).trim()
                val valuePart = content.substring(colon + 1).trim()
                if (valuePart.isNotEmpty()) {
                    out[key] = Node.Scalar(unquote(valuePart))
                    i++
                } else {
                    val childIndent = peekIndent(lines, i + 1)
                    if (childIndent > indent) {
                        val (child, next) = readBlock(lines, i + 1, childIndent)
                        out[key] = Node.Map(child)
                        i = next
                    } else {
                        out[key] = Node.Scalar("")
                        i++
                    }
                }
            }
            return out to i
        }

        private fun peekIndent(lines: List<String>, index: Int): Int =
            if (index >= lines.size) -1 else lines[index].takeWhile { it == ' ' }.length

        private fun unquote(value: String): String {
            val trimmed = value.trim()
            return when {
                trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"' ->
                    trimmed.substring(1, trimmed.length - 1)
                trimmed.length >= 2 && trimmed.first() == '\'' && trimmed.last() == '\'' ->
                    trimmed.substring(1, trimmed.length - 1)
                else -> trimmed
            }
        }
    }
}
