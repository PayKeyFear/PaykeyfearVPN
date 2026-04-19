package com.paykeyfear.vpn.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ConfigEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PaykeyfearDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
}
