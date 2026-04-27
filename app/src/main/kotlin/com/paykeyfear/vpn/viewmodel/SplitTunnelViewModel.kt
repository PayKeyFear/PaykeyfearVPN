package com.paykeyfear.vpn.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import com.paykeyfear.vpn.service.TunnelController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

data class SplitTunnelUiState(
    val mode: SplitTunnelMode = SplitTunnelMode.Off,
    val selected: Set<String> = emptySet(),
    val apps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
    val ruBypass: Boolean = false,
    val vpnActive: Boolean = false,
) {
    val filtered: List<InstalledApp> =
        if (query.isBlank()) {
            apps.filter { !it.isSystem }
        } else {
            val q = query.trim().lowercase()
            apps.filter { !it.isSystem && (it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)) }
        }
}

@HiltViewModel
class SplitTunnelViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
    private val tunnelController: TunnelController,
) : ViewModel() {
    private val apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val query = MutableStateFlow("")

    val state: StateFlow<SplitTunnelUiState> =
        combine(
            preferences.splitTunnelMode,
            preferences.splitTunnelPackages,
            preferences.ruBypassEnabled,
            apps,
            combine(loading, query, tunnelController.state) { l, q, ts -> Triple(l, q, ts) },
        ) { mode, pkgs, ruBypass, list, (isLoading, q, tunnelState) ->
            // selected first (false < true), then user apps, then alpha
            val sorted = list.sortedWith(
                compareBy(
                    { it.packageName !in pkgs },
                    { it.isSystem },
                    { it.label.lowercase() },
                ),
            )
            SplitTunnelUiState(
                mode = mode,
                selected = pkgs,
                apps = sorted,
                isLoading = isLoading,
                query = q,
                ruBypass = ruBypass,
                vpnActive = tunnelState is TunnelState.Connected || tunnelState == TunnelState.Connecting,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SplitTunnelUiState(),
        )

    init {
        viewModelScope.launch {
            apps.value = loadInstalledApps()
            loading.value = false
        }
    }

    fun setMode(mode: SplitTunnelMode) {
        if (state.value.vpnActive) return
        viewModelScope.launch { preferences.setSplitTunnelMode(mode) }
    }

    fun resetRules() {
        if (state.value.vpnActive) return
        viewModelScope.launch {
            preferences.setSplitTunnelPackages(emptySet())
            preferences.setSplitTunnelMode(SplitTunnelMode.Off)
        }
    }

    fun toggle(pkg: String, checked: Boolean) {
        if (state.value.vpnActive) return
        viewModelScope.launch {
            // Don't read state.value — it's produced by a WhileSubscribed
            // StateFlow that stays at initialValue until someone actually
            // collects it, so in unit tests the set we write back is
            // computed against an empty seed. Reading the underlying
            // preferences flow directly gives us the ground truth
            // regardless of collection timing.
            val current = preferences.splitTunnelPackages.first()
            val next = current.toMutableSet().apply {
                if (checked) add(pkg) else remove(pkg)
            }
            preferences.setSplitTunnelPackages(next)
        }
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun setRuBypass(enabled: Boolean) {
        if (state.value.vpnActive) return
        viewModelScope.launch { preferences.setRuBypassEnabled(enabled) }
    }

    private suspend fun loadInstalledApps(): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledPackages(PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .filter { pkg ->
                    // Only show apps that have network needs.
                    pkg.requestedPermissions?.any { it == android.Manifest.permission.INTERNET } == true
                }
                .map { pkg ->
                    val ai = pkg.applicationInfo
                    InstalledApp(
                        packageName = pkg.packageName,
                        label = ai?.loadLabel(pm)?.toString() ?: pkg.packageName,
                        isSystem = ai != null &&
                            (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }
                .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
                .toList()
        }
}
