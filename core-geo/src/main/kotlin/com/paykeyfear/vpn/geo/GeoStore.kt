package com.paykeyfear.vpn.geo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Persists GeoIP/Geosite payloads under `filesDir/geo/` and exposes a
 * read path that falls back to the asset shipped in the APK when no
 * persisted copy exists.
 */
class GeoStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "geo").apply { mkdirs() }

    suspend fun readRuCidr(): String = read(CIDR_NAME, ASSET_CIDR)

    suspend fun readRuDomains(): String = read(DOMAINS_NAME, ASSET_DOMAINS)

    suspend fun writeRuCidr(content: String): WriteResult = write(CIDR_NAME, content)

    suspend fun writeRuDomains(content: String): WriteResult = write(DOMAINS_NAME, content)

    suspend fun ruCidrUpdatedAt(): Long = File(dir, CIDR_NAME).lastModified()

    suspend fun ruDomainsUpdatedAt(): Long = File(dir, DOMAINS_NAME).lastModified()

    private suspend fun read(name: String, assetPath: String): String = withContext(Dispatchers.IO) {
        val persisted = File(dir, name)
        if (persisted.isFile && persisted.length() > 0) {
            persisted.readText()
        } else {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }
    }

    private suspend fun write(name: String, content: String): WriteResult = withContext(Dispatchers.IO) {
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(content)
        val renamed = tmp.renameTo(target)
        if (!renamed) {
            tmp.delete()
            WriteResult(false, target, sha256(content))
        } else {
            WriteResult(true, target, sha256(content))
        }
    }

    data class WriteResult(val ok: Boolean, val file: File, val sha256Hex: String)

    companion object {
        private const val CIDR_NAME = "ru-cidr.txt"
        private const val DOMAINS_NAME = "ru-domains.txt"
        private const val ASSET_CIDR = "geo/ru-cidr.txt"
        private const val ASSET_DOMAINS = "geo/ru-domains.txt"

        fun sha256(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return buildString(digest.size * 2) {
                for (b in digest) {
                    val v = b.toInt() and 0xFF
                    append(HEX[v ushr 4])
                    append(HEX[v and 0x0F])
                }
            }
        }

        private val HEX = "0123456789abcdef".toCharArray()
    }
}
