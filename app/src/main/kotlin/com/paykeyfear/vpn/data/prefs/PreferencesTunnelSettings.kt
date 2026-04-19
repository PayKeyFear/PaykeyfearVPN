package com.paykeyfear.vpn.data.prefs

import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.data.repository.ConfigRepository
import com.paykeyfear.vpn.service.SplitTunnelConfig
import com.paykeyfear.vpn.service.TunnelSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PreferencesTunnelSettings
    @Inject
    constructor(
        private val preferences: PreferencesRepository,
        private val configs: ConfigRepository,
    ) : TunnelSettings {
        override suspend fun splitTunnel(): SplitTunnelConfig =
            SplitTunnelConfig(
                mode = preferences.splitTunnelMode.first(),
                packages = preferences.splitTunnelPackages.first(),
            )

        override suspend fun selectedConfig(): ConnectionConfig? {
            val id = preferences.selectedConfigId.first() ?: return null
            return configs.findById(id)
        }
    }
