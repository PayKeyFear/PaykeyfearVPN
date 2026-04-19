package com.paykeyfear.vpn.data.repository

import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.data.local.ConfigDao
import com.paykeyfear.vpn.data.local.ConfigEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class RoomConfigRepository(
    private val dao: ConfigDao,
    private val json: Json,
    private val clock: () -> Long,
) : ConfigRepository {
    // Hilt can't inject the `clock` lambda — function types aren't
    // resolvable through the DI graph without an extra @Provides method,
    // and we don't want a runtime entry just to read the wall clock.
    // The secondary @Inject-able constructor hardwires the production
    // clock; unit tests use the 3-arg primary constructor to plug in a
    // deterministic time source.
    @Inject
    constructor(dao: ConfigDao, json: Json) : this(dao, json, System::currentTimeMillis)

    override fun observeAll(): Flow<List<ConnectionConfig>> =
        dao.observeAll().map { rows -> rows.map(::decode) }

    override suspend fun findById(id: String): ConnectionConfig? =
        dao.findById(id)?.let(::decode)

    override suspend fun upsert(config: ConnectionConfig) {
        dao.upsert(
            ConfigEntity(
                id = config.id,
                displayName = config.displayName,
                protocol = config.protocol.name,
                endpointHost = config.endpoint.host,
                endpointPort = config.endpoint.port,
                payloadJson = json.encodeToString(ConnectionConfig.serializer(), config),
                createdAtEpochMs = clock(),
            ),
        )
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun markUsed(id: String, epochMs: Long) = dao.markUsed(id, epochMs)

    private fun decode(entity: ConfigEntity): ConnectionConfig =
        json.decodeFromString(ConnectionConfig.serializer(), entity.payloadJson)
}
