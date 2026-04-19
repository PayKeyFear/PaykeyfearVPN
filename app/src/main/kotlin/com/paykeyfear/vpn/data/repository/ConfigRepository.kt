package com.paykeyfear.vpn.data.repository

import com.paykeyfear.vpn.core.model.ConnectionConfig
import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    fun observeAll(): Flow<List<ConnectionConfig>>

    suspend fun findById(id: String): ConnectionConfig?

    suspend fun upsert(config: ConnectionConfig)

    suspend fun delete(id: String)

    suspend fun markUsed(id: String, epochMs: Long = System.currentTimeMillis())
}
