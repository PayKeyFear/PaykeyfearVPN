package com.paykeyfear.vpn.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStorePreferencesRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : PreferencesRepository {
        override val selectedConfigId: Flow<String?> =
            dataStore.data.map { it[KEY_SELECTED_ID] }

        override val dynamicColorEnabled: Flow<Boolean> =
            dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }

        override val connectOnBoot: Flow<Boolean> =
            dataStore.data.map { it[KEY_CONNECT_ON_BOOT] ?: false }

        override val splitTunnelMode: Flow<SplitTunnelMode> =
            dataStore.data.map { SplitTunnelMode.fromStorageValue(it[KEY_SPLIT_MODE]) }

        override val splitTunnelPackages: Flow<Set<String>> =
            dataStore.data.map { it[KEY_SPLIT_PACKAGES] ?: emptySet() }

        override suspend fun setSelectedConfigId(id: String?) {
            dataStore.edit { prefs ->
                if (id == null) prefs.remove(KEY_SELECTED_ID) else prefs[KEY_SELECTED_ID] = id
            }
        }

        override suspend fun setDynamicColorEnabled(enabled: Boolean) {
            dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
        }

        override suspend fun setConnectOnBoot(enabled: Boolean) {
            dataStore.edit { it[KEY_CONNECT_ON_BOOT] = enabled }
        }

        override suspend fun setSplitTunnelMode(mode: SplitTunnelMode) {
            dataStore.edit { it[KEY_SPLIT_MODE] = mode.name }
        }

        override suspend fun setSplitTunnelPackages(packages: Set<String>) {
            dataStore.edit { it[KEY_SPLIT_PACKAGES] = packages }
        }

        private companion object {
            val KEY_SELECTED_ID = stringPreferencesKey("selected_config_id")
            val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
            val KEY_CONNECT_ON_BOOT = booleanPreferencesKey("connect_on_boot")
            val KEY_SPLIT_MODE = stringPreferencesKey("split_tunnel_mode")
            val KEY_SPLIT_PACKAGES = stringSetPreferencesKey("split_tunnel_packages")
        }
    }
