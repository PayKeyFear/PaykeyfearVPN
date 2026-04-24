package com.paykeyfear.vpn.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pulls raw text payloads for RU geoip (CIDR per line) and geosite
 * (v2fly/domain-list-community format). Implementations are stateless;
 * callers handle persistence and caching.
 */
interface GeoSource {
    suspend fun fetchRuCidr(): String
    suspend fun fetchRuDomains(): String
}

class HttpGeoSource(
    private val cidrUrl: String = DEFAULT_CIDR_URL,
    private val domainsUrl: String = DEFAULT_DOMAINS_URL,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : GeoSource {
    override suspend fun fetchRuCidr(): String = get(cidrUrl)

    override suspend fun fetchRuDomains(): String = get(domainsUrl)

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "text/plain")
            setRequestProperty("User-Agent", "PaykeyfearVPN-geo/1.0")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("GET $url -> HTTP $code")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_CIDR_URL =
            "https://raw.githubusercontent.com/v2fly/geoip/release/text/ru.txt"
        const val DEFAULT_DOMAINS_URL =
            "https://raw.githubusercontent.com/v2fly/domain-list-community/master/data/category-ru"
    }
}
