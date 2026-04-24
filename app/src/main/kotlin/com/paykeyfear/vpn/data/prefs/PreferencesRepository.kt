package com.paykeyfear.vpn.data.prefs

import com.paykeyfear.vpn.core.model.SplitTunnelMode
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val selectedConfigId: Flow<String?>

    val dynamicColorEnabled: Flow<Boolean>

    val connectOnBoot: Flow<Boolean>

    val splitTunnelMode: Flow<SplitTunnelMode>

    val splitTunnelPackages: Flow<Set<String>>

    val ruBypassEnabled: Flow<Boolean>

    suspend fun setSelectedConfigId(id: String?)

    suspend fun setDynamicColorEnabled(enabled: Boolean)

    suspend fun setConnectOnBoot(enabled: Boolean)

    suspend fun setSplitTunnelMode(mode: SplitTunnelMode)

    suspend fun setSplitTunnelPackages(packages: Set<String>)

    suspend fun setRuBypassEnabled(enabled: Boolean)
}
