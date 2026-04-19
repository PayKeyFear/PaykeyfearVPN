package com.paykeyfear.vpn.config

/** Raw input handed to a parser. */
sealed interface ConfigSource {
    val name: String

    data class Text(override val name: String, val content: String) : ConfigSource

    data class Bytes(override val name: String, val content: ByteArray) : ConfigSource {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Bytes && name == other.name && content.contentEquals(other.content))

        override fun hashCode(): Int = 31 * name.hashCode() + content.contentHashCode()
    }
}
