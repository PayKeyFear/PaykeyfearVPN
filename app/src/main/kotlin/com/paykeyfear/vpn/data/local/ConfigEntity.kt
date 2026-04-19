package com.paykeyfear.vpn.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_configs")
data class ConfigEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val protocol: String,
    val endpointHost: String,
    val endpointPort: Int,
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long? = null,
)
