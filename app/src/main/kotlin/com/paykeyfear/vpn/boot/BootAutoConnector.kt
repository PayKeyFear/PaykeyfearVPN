package com.paykeyfear.vpn.boot

import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import com.paykeyfear.vpn.data.repository.ConfigRepository
import kotlinx.coroutines.flow.first

/**
 * Returns the config that the boot receiver should auto-start, or null if
 * auto-start is disabled / no config is selected / the selected config is
 * missing. Pure logic — no Android deps, so it's unit-testable.
 */
object BootAutoConnector {
    suspend fun resolvePendingConfig(
        preferences: PreferencesRepository,
        repository: ConfigRepository,
    ): ConnectionConfig? {
        if (!preferences.connectOnBoot.first()) return null
        val selectedId = preferences.selectedConfigId.first() ?: return null
        return repository.findById(selectedId)
    }
}
