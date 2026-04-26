package com.paykeyfear.vpn.testing

import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePreferencesRepository(
    selectedId: String? = null,
    connectOnBoot: Boolean = false,
    splitMode: SplitTunnelMode = SplitTunnelMode.Off,
    splitPackages: Set<String> = emptySet(),
    ruBypass: Boolean = false,
) : PreferencesRepository {
    private val selectedFlow = MutableStateFlow(selectedId)
    private val connectOnBootFlow = MutableStateFlow(connectOnBoot)
    private val splitModeFlow = MutableStateFlow(splitMode)
    private val splitPackagesFlow = MutableStateFlow(splitPackages)
    private val ruBypassFlow = MutableStateFlow(ruBypass)

    override val selectedConfigId: Flow<String?> = selectedFlow.asStateFlow()
    override val connectOnBoot: Flow<Boolean> = connectOnBootFlow.asStateFlow()
    override val splitTunnelMode: Flow<SplitTunnelMode> = splitModeFlow.asStateFlow()
    override val splitTunnelPackages: Flow<Set<String>> = splitPackagesFlow.asStateFlow()
    override val ruBypassEnabled: Flow<Boolean> = ruBypassFlow.asStateFlow()

    override suspend fun setSelectedConfigId(id: String?) {
        selectedFlow.value = id
    }

    override suspend fun setConnectOnBoot(enabled: Boolean) {
        connectOnBootFlow.value = enabled
    }

    override suspend fun setSplitTunnelMode(mode: SplitTunnelMode) {
        splitModeFlow.value = mode
    }

    override suspend fun setSplitTunnelPackages(packages: Set<String>) {
        splitPackagesFlow.value = packages
    }

    override suspend fun setRuBypassEnabled(enabled: Boolean) {
        ruBypassFlow.value = enabled
    }

    fun currentSelectedId(): String? = selectedFlow.value

    fun currentSplitPackages(): Set<String> = splitPackagesFlow.value

    fun currentSplitMode(): SplitTunnelMode = splitModeFlow.value

    fun currentRuBypass(): Boolean = ruBypassFlow.value
}
