package com.paykeyfear.vpn.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.BuildConfig
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.ui.theme.AccentGreen
import com.paykeyfear.vpn.ui.theme.AccentGreenDim
import com.paykeyfear.vpn.ui.theme.BorderColor
import com.paykeyfear.vpn.ui.theme.SurfaceBg
import com.paykeyfear.vpn.ui.theme.SurfaceCard
import com.paykeyfear.vpn.ui.theme.TextMuted
import com.paykeyfear.vpn.ui.theme.TextPrimary
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
    Column(
        Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(vertical = 8.dp),
    ) {
        SettingsTitle()
        SettingsSwitchItem(
            title = stringResource(R.string.settings_dynamic_color),
            subtitle = stringResource(R.string.settings_dynamic_color_desc),
            checked = state.dynamicColorEnabled,
            onCheckedChange = viewModel::setDynamicColor,
        )
        SettingsDivider()
        SettingsSwitchItem(
            title = stringResource(R.string.settings_connect_on_boot),
            subtitle = stringResource(R.string.settings_connect_on_boot_desc),
            checked = state.connectOnBoot,
            onCheckedChange = viewModel::setConnectOnBoot,
        )
        SettingsDivider()
        SettingsArrowItem(stringResource(R.string.split_tunnel_title), stringResource(R.string.split_tunnel_desc), onSplitTunnelClick)
        SettingsDivider()
        SettingsArrowItem(stringResource(R.string.privacy_title), stringResource(R.string.privacy_subtitle), onPrivacyClick)
        SettingsDivider()
        SettingsArrowItem(stringResource(R.string.logs_title), stringResource(R.string.logs_subtitle), onLogsClick)
        SettingsDivider()
        SettingsArrowItem(
            stringResource(R.string.settings_about),
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            onAboutClick,
        )
    }
}

@Composable
private fun SettingsTitle() {
    Text(
        "Настройки",
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, color = TextPrimary) },
        supportingContent = { Text(subtitle, color = TextMuted) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SurfaceBg,
                    checkedTrackColor = AccentGreen,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceCard,
                ),
            )
        },
        colors = ListItemDefaults.colors(containerColor = SurfaceBg),
    )
}

@Composable
private fun SettingsArrowItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = TextPrimary) },
        supportingContent = { Text(subtitle, color = TextMuted) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = SurfaceBg),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = BorderColor, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
}
