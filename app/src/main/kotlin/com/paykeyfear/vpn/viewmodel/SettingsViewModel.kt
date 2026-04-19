package com.paykeyfear.vpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val dynamicColorEnabled: Boolean = true,
    val connectOnBoot: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferences: PreferencesRepository,
    ) : ViewModel() {
        val state: StateFlow<SettingsUiState> =
            combine(preferences.dynamicColorEnabled, preferences.connectOnBoot) { dc, cob ->
                SettingsUiState(dynamicColorEnabled = dc, connectOnBoot = cob)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = SettingsUiState(),
            )

        fun setDynamicColor(enabled: Boolean) {
            viewModelScope.launch { preferences.setDynamicColorEnabled(enabled) }
        }

        fun setConnectOnBoot(enabled: Boolean) {
            viewModelScope.launch { preferences.setConnectOnBoot(enabled) }
        }
    }
