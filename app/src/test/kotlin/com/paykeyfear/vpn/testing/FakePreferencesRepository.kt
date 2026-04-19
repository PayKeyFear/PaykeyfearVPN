package com.paykeyfear.vpn.testing

import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePreferencesRepository(
    selectedId: String? = null,
    dynamicColor: Boolean = true,
    connectOnBoot: Boolean = false,
    splitMode: SplitTunnelMode = SplitTunnelMode.Off,
    splitPackages: Set<String> = emptySet(),
) : PreferencesRepository {
    private val _selected = MutableStateFlow(selectedId)
    private val _dynamicColor = MutableStateFlow(dynamicColor)
    private val _connectOnBoot = MutableStateFlow(connectOnBoot)
    private val _splitMode = MutableStateFlow(splitMode)
    private val _splitPackages = MutableStateFlow(splitPackages)

    override val selectedConfigId: Flow<String?> = _selected.asStateFlow()
    override val dynamicColorEnabled: Flow<Boolean> = _dynamicColor.asStateFlow()
    override val connectOnBoot: Flow<Boolean> = _connectOnBoot.asStateFlow()
    override val splitTunnelMode: Flow<SplitTunnelMode> = _splitMode.asStateFlow()
    override val splitTunnelPackages: Flow<Set<String>> = _splitPackages.asStateFlow()

    override suspend fun setSelectedConfigId(id: String?) {
        _selected.value = id
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        _dynamicColor.value = enabled
    }

    override suspend fun setConnectOnBoot(enabled: Boolean) {
        _connectOnBoot.value = enabled
    }

    override suspend fun setSplitTunnelMode(mode: SplitTunnelMode) {
        _splitMode.value = mode
    }

    override suspend fun setSplitTunnelPackages(packages: Set<String>) {
        _splitPackages.value = packages
    }

    fun currentSelectedId(): String? = _selected.value

    fun currentSplitPackages(): Set<String> = _splitPackages.value

    fun currentSplitMode(): SplitTunnelMode = _splitMode.value
}
