package com.paykeyfear.vpn.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM connection_configs ORDER BY lastUsedAtEpochMs DESC, createdAtEpochMs DESC")
    fun observeAll(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM connection_configs WHERE id = :id")
    suspend fun findById(id: String): ConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConfigEntity)

    @Query("DELETE FROM connection_configs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE connection_configs SET lastUsedAtEpochMs = :ts WHERE id = :id")
    suspend fun markUsed(id: String, ts: Long)
}
