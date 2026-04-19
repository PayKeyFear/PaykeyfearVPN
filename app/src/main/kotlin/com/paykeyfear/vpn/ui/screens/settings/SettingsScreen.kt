package com.paykeyfear.vpn.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.BuildConfig
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onSplitTunnelClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
            supportingContent = { Text(stringResource(R.string.settings_dynamic_color_desc)) },
            trailingContent = {
                Switch(
                    checked = state.dynamicColorEnabled,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_connect_on_boot)) },
            supportingContent = { Text(stringResource(R.string.settings_connect_on_boot_desc)) },
            trailingContent = {
                Switch(
                    checked = state.connectOnBoot,
                    onCheckedChange = viewModel::setConnectOnBoot,
                )
            },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.split_tunnel_title)) },
            supportingContent = { Text(stringResource(R.string.split_tunnel_desc)) },
            modifier = Modifier.clickable(onClick = onSplitTunnelClick),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.privacy_title)) },
            supportingContent = { Text(stringResource(R.string.privacy_subtitle)) },
            modifier = Modifier.clickable(onClick = onPrivacyClick),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.logs_title)) },
            supportingContent = { Text(stringResource(R.string.logs_subtitle)) },
            modifier = Modifier.clickable(onClick = onLogsClick),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about)) },
            supportingContent = {
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            modifier = Modifier.clickable(onClick = onAboutClick),
        )
    }
}
