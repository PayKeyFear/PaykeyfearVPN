package com.paykeyfear.vpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paykeyfear.vpn.config.ConfigParserRegistry
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import com.paykeyfear.vpn.data.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val text: String = "",
    val isImporting: Boolean = false,
    val importedName: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ImportViewModel
    @Inject
    constructor(
        private val registry: ConfigParserRegistry,
        private val repository: ConfigRepository,
        private val preferences: PreferencesRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ImportUiState())
        val state: StateFlow<ImportUiState> = _state.asStateFlow()

        fun onTextChanged(text: String) {
            _state.update { it.copy(text = text, error = null, importedName = null) }
        }

        fun onImportClicked() {
            val input = _state.value.text.takeIf { it.isNotBlank() } ?: return
            _state.update { it.copy(isImporting = true, error = null) }
            viewModelScope.launch {
                val result = runCatching {
                    val parsed = registry.parse(ConfigSource.Text("pasted", input))
                    repository.upsert(parsed)
                    preferences.setSelectedConfigId(parsed.id)
                    parsed
                }
                _state.update { prev ->
                    result.fold(
                        onSuccess = { cfg ->
                            prev.copy(isImporting = false, importedName = cfg.displayName, error = null)
                        },
                        onFailure = { err ->
                            prev.copy(isImporting = false, error = err.message ?: "Failed to import")
                        },
                    )
                }
            }
        }
    }
