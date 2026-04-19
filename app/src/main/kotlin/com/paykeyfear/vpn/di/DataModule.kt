package com.paykeyfear.vpn.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.paykeyfear.vpn.data.local.ConfigDao
import com.paykeyfear.vpn.data.local.PaykeyfearDatabase
import com.paykeyfear.vpn.data.prefs.DataStorePreferencesRepository
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import com.paykeyfear.vpn.data.prefs.PreferencesTunnelSettings
import com.paykeyfear.vpn.data.repository.ConfigRepository
import com.paykeyfear.vpn.data.repository.RoomConfigRepository
import com.paykeyfear.vpn.service.TunnelSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): PaykeyfearDatabase =
        Room.databaseBuilder(context, PaykeyfearDatabase::class.java, "paykeyfear.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideConfigDao(db: PaykeyfearDatabase): ConfigDao = db.configDao()

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, "datastore/paykeyfear.preferences_pb") },
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: RoomConfigRepository): ConfigRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: DataStorePreferencesRepository): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindTunnelSettings(impl: PreferencesTunnelSettings): TunnelSettings
}
