package com.paykeyfear.vpn.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.BuildConfig
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.ui.theme.AccentGreen
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
            title = stringResource(R.string.settings_connect_on_boot),
            subtitle = stringResource(R.string.settings_connect_on_boot_desc),
            checked = state.connectOnBoot,
            onCheckedChange = viewModel::setConnectOnBoot,
        )
        SettingsDivider()
        SettingsLanguageItem()
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

private data class LangOption(val tag: String?, val labelRes: Int)

private val LANG_OPTIONS = listOf(
    LangOption(null, R.string.settings_language_system),
    LangOption("en", R.string.settings_language_en),
    LangOption("ru", R.string.settings_language_ru),
)

@Composable
private fun SettingsLanguageItem() {
    var expanded by remember { mutableStateOf(false) }
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        .takeIf { it.isNotBlank() }
        ?.substringBefore(',')
        ?.substringBefore('-')
    val current = LANG_OPTIONS.find { it.tag == currentTag } ?: LANG_OPTIONS[0]

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_language), color = TextPrimary) },
        supportingContent = { Text(stringResource(current.labelRes), color = TextMuted) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LANG_OPTIONS.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(stringResource(opt.labelRes)) },
                        onClick = {
                            expanded = false
                            val locales = if (opt.tag == null) {
                                LocaleListCompat.getEmptyLocaleList()
                            } else {
                                LocaleListCompat.forLanguageTags(opt.tag)
                            }
                            AppCompatDelegate.setApplicationLocales(locales)
                        },
                    )
                }
            }
        },
        modifier = Modifier.clickable { expanded = true },
        colors = ListItemDefaults.colors(containerColor = SurfaceBg),
    )
}
