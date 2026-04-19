package com.paykeyfear.vpn.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
) {
    val filtered: List<InstalledApp> =
        if (query.isBlank()) {
            apps
        } else {
            val q = query.trim().lowercase()
            apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
}

@HiltViewModel
class SplitTunnelViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PreferencesRepository,
    ) : ViewModel() {
        private val apps = MutableStateFlow<List<InstalledApp>>(emptyList())
        private val loading = MutableStateFlow(true)
        private val query = MutableStateFlow("")

        val state: StateFlow<SplitTunnelUiState> =
            combine(
                preferences.splitTunnelMode,
                preferences.splitTunnelPackages,
                apps,
                loading,
                query,
            ) { mode, pkgs, list, isLoading, q ->
                SplitTunnelUiState(
                    mode = mode,
                    selected = pkgs,
                    apps = list,
                    isLoading = isLoading,
                    query = q,
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
            viewModelScope.launch { preferences.setSplitTunnelMode(mode) }
        }

        fun toggle(pkg: String, checked: Boolean) {
            viewModelScope.launch {
                val next = state.value.selected.toMutableSet().apply {
                    if (checked) add(pkg) else remove(pkg)
                }
                preferences.setSplitTunnelPackages(next)
            }
        }

        fun setQuery(q: String) {
            query.value = q
        }

        private suspend fun loadInstalledApps(): List<InstalledApp> =
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledPackages(PackageManager.GET_META_DATA)
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
