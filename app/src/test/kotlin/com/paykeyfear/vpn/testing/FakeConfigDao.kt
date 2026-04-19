package com.paykeyfear.vpn.testing

import com.paykeyfear.vpn.data.local.ConfigDao
import com.paykeyfear.vpn.data.local.ConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ConfigDao] used in place of Room for unit tests. */
class FakeConfigDao : ConfigDao {
    private val entries = MutableStateFlow<Map<String, ConfigEntity>>(emptyMap())

    override fun observeAll(): Flow<List<ConfigEntity>> =
        entries.map { snapshot ->
            snapshot.values.sortedWith(
                compareByDescending<ConfigEntity> { it.lastUsedAtEpochMs ?: Long.MIN_VALUE }
                    .thenByDescending { it.createdAtEpochMs },
            )
        }

    override suspend fun findById(id: String): ConfigEntity? = entries.value[id]

    override suspend fun upsert(entity: ConfigEntity) {
        entries.value = entries.value + (entity.id to entity)
    }

    override suspend fun delete(id: String) {
        entries.value = entries.value - id
    }

    override suspend fun markUsed(id: String, ts: Long) {
        val existing = entries.value[id] ?: return
        entries.value = entries.value + (id to existing.copy(lastUsedAtEpochMs = ts))
    }
}
