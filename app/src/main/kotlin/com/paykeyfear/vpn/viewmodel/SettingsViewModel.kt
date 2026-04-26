package com.paykeyfear.vpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val connectOnBoot: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferences: PreferencesRepository,
    ) : ViewModel() {
        val state: StateFlow<SettingsUiState> =
            preferences.connectOnBoot.map { cob ->
                SettingsUiState(connectOnBoot = cob)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = SettingsUiState(),
            )

        fun setConnectOnBoot(enabled: Boolean) {
            viewModelScope.launch { preferences.setConnectOnBoot(enabled) }
        }
    }
