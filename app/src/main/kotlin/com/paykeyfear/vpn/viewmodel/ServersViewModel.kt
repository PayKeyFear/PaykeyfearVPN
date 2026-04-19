package com.paykeyfear.vpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import com.paykeyfear.vpn.data.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ServersUiState(
    val servers: List<ConnectionConfig> = emptyList(),
    val selectedId: String? = null,
)

@HiltViewModel
class ServersViewModel
    @Inject
    constructor(
        private val repository: ConfigRepository,
        private val preferences: PreferencesRepository,
    ) : ViewModel() {
        val state: StateFlow<ServersUiState> =
            combine(repository.observeAll(), preferences.selectedConfigId) { servers, selId ->
                ServersUiState(servers = servers, selectedId = selId)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = ServersUiState(),
            )

        fun select(id: String) {
            viewModelScope.launch { preferences.setSelectedConfigId(id) }
        }

        fun delete(id: String) {
            viewModelScope.launch {
                repository.delete(id)
                if (state.value.selectedId == id) {
                    preferences.setSelectedConfigId(null)
                }
            }
        }
    }
