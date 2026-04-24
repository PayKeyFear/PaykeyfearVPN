package com.paykeyfear.vpn.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.viewmodel.SplitTunnelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelScreen(
    onBack: () -> Unit = {},
    viewModel: SplitTunnelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.split_tunnel_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.split_ru_bypass_title)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.split_ru_bypass_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.ruBypass,
                        onCheckedChange = viewModel::setRuBypass,
                    )
                },
            )
            HorizontalDivider()
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SplitTunnelMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, SplitTunnelMode.entries.size),
                        icon = {},
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        SplitTunnelMode.Off -> R.string.split_mode_off
                                        SplitTunnelMode.Allowlist -> R.string.split_mode_allow
                                        SplitTunnelMode.Denylist -> R.string.split_mode_deny
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Text(
                stringResource(
                    when (state.mode) {
                        SplitTunnelMode.Off -> R.string.split_desc_off
                        SplitTunnelMode.Allowlist -> R.string.split_desc_allow
                        SplitTunnelMode.Denylist -> R.string.split_desc_deny
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.split_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.filtered.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.split_no_apps)) }
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(state.filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in state.selected
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = {
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            },
                            leadingContent = {
                                if (app.isSystem) {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            },
                            trailingContent = {
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { viewModel.toggle(app.packageName, it) },
                                    enabled = state.mode != SplitTunnelMode.Off,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
