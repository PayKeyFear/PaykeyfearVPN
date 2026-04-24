package com.paykeyfear.vpn.geo

import timber.log.Timber

/**
 * Orchestrates the Source → Store pipeline: loads the latest payload from
 * the configured [GeoSource], verifies it's non-empty, and atomically
 * persists it via [GeoStore]. Read paths delegate straight to the store.
 *
 * A refresh failure never corrupts the on-disk copy — the store's
 * write-then-rename guarantees the previous payload stays readable.
 */
class GeoRepository(
    private val source: GeoSource,
    private val store: GeoStore,
) {
    suspend fun readRuCidr(): String = store.readRuCidr()

    suspend fun readRuDomains(): String = store.readRuDomains()

    suspend fun refresh(): RefreshReport {
        val cidr = runCatching { source.fetchRuCidr() }.fold(
            onSuccess = { persistCidr(it) },
            onFailure = { RefreshOne.Failure(it) },
        )
        val domains = runCatching { source.fetchRuDomains() }.fold(
            onSuccess = { persistDomains(it) },
            onFailure = { RefreshOne.Failure(it) },
        )
        return RefreshReport(cidr, domains).also(::logReport)
    }

    private suspend fun persistCidr(text: String): RefreshOne {
        if (!looksLikeCidrFile(text)) return RefreshOne.Skipped("empty or malformed CIDR payload")
        val result = store.writeRuCidr(text)
        return RefreshOne.Success(text.length, result.sha256Hex)
    }

    private suspend fun persistDomains(text: String): RefreshOne {
        if (text.isBlank()) return RefreshOne.Skipped("empty domains payload")
        val result = store.writeRuDomains(text)
        return RefreshOne.Success(text.length, result.sha256Hex)
    }

    private fun looksLikeCidrFile(text: String): Boolean {
        val firstMeaningful = text.lineSequence()
            .map { it.substringBefore('#').trim() }
            .firstOrNull { it.isNotEmpty() } ?: return false
        return '/' in firstMeaningful
    }

    private fun logReport(report: RefreshReport) {
        Timber.tag(TAG).i("refresh: cidr=%s domains=%s", report.cidr, report.domains)
    }

    data class RefreshReport(val cidr: RefreshOne, val domains: RefreshOne) {
        val allOk: Boolean get() = cidr is RefreshOne.Success && domains is RefreshOne.Success
    }

    sealed interface RefreshOne {
        data class Success(val bytes: Int, val sha256Hex: String) : RefreshOne
        data class Skipped(val reason: String) : RefreshOne
        data class Failure(val error: Throwable) : RefreshOne
    }

    private companion object {
        const val TAG = "GeoRepository"
    }
}
