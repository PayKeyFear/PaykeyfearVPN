package com.paykeyfear.vpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.core.model.TunnelStats
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import com.paykeyfear.vpn.data.repository.ConfigRepository
import com.paykeyfear.vpn.service.TunnelController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val tunnelState: TunnelState = TunnelState.Disconnected,
    val selected: ConnectionConfig? = null,
    val hasAnyServer: Boolean = false,
    val stats: TunnelStats = TunnelStats.ZERO,
) {
    val isConnected: Boolean = tunnelState is TunnelState.Connected

    val statusLabel: String =
        when (tunnelState) {
            TunnelState.Disconnected, TunnelState.Disconnecting -> "Disconnected"
            TunnelState.Connecting -> "Connecting…"
            is TunnelState.Connected -> "Connected to ${tunnelState.protocol.displayName}"
            is TunnelState.Error -> "Error: ${tunnelState.message}"
        }
}

/**
 * Events emitted to the UI layer to trigger Android-side actions the ViewModel
 * itself can't perform (requesting VPN permission, starting the service with a
 * config).
 */
sealed interface HomeEvent {
    data class RequestVpnPermissionThenConnect(val config: ConnectionConfig) : HomeEvent

    data object StopTunnel : HomeEvent
}

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    private val repository: ConfigRepository,
    private val controller: TunnelController,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val pendingEvents = MutableStateFlow<HomeEvent?>(null)

    val state: StateFlow<HomeUiState> =
        combine(
            controller.state,
            repository.observeAll(),
            preferences.selectedConfigId,
            controller.stats,
        ) { tunnel, servers, selId, stats ->
            val chosen = servers.firstOrNull { it.id == selId } ?: servers.firstOrNull()
            HomeUiState(
                tunnelState = tunnel,
                selected = chosen,
                hasAnyServer = servers.isNotEmpty(),
                stats = stats,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = HomeUiState(),
        )

    val events: StateFlow<HomeEvent?> = pendingEvents

    fun toggle() {
        val s = state.value
        if (s.isConnected) {
            pendingEvents.value = HomeEvent.StopTunnel
        } else {
            val cfg = s.selected ?: return
            viewModelScope.launch { repository.markUsed(cfg.id) }
            pendingEvents.value = HomeEvent.RequestVpnPermissionThenConnect(cfg)
        }
    }

    fun consumeEvent() {
        pendingEvents.value = null
    }
}
