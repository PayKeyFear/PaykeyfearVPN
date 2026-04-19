package com.paykeyfear.vpn.testing

import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.data.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal in-memory [ConfigRepository] used by ViewModel unit tests. */
class FakeConfigRepository(initial: List<ConnectionConfig> = emptyList()) : ConfigRepository {
    private val state = MutableStateFlow(initial)
    val markedUsed = mutableListOf<Pair<String, Long>>()

    override fun observeAll(): Flow<List<ConnectionConfig>> = state.asStateFlow()

    override suspend fun findById(id: String): ConnectionConfig? =
        state.value.firstOrNull { it.id == id }

    override suspend fun upsert(config: ConnectionConfig) {
        state.value = state.value.filterNot { it.id == config.id } + config
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun markUsed(id: String, epochMs: Long) {
        markedUsed += id to epochMs
    }
}
