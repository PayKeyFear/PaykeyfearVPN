package com.paykeyfear.vpn.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.paykeyfear.vpn.BuildConfig
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.core.logging.NativeBackendVersions

@Composable
fun AboutScreen() {
    val backends = remember {
        // Prime all three lookups so lastError captures the most recent
        // (deterministic: awg runs first, the rest would throw the same
        // class-loading error if the .aar is missing).
        val awg = NativeBackendVersions.awg()
        val vless = NativeBackendVersions.vless()
        val hy2 = NativeBackendVersions.hysteria2()
        listOf(
            "AmneziaWG" to (awg ?: "not bundled (noop mode)"),
            "VLESS" to (vless ?: "not bundled (noop mode)"),
            "Hysteria2" to (hy2 ?: "not bundled (noop mode)"),
        )
    }
    val lookupError = remember { NativeBackendVersions.lastError }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.app_name)) },
            supportingContent = {
                Text(
                    text = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
        HorizontalDivider()
        Text(
            text = stringResource(R.string.about_native_backends),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        backends.forEach { (name, version) ->
            ListItem(
                headlineContent = { Text(name) },
                supportingContent = {
                    Text(
                        text = version,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                },
            )
            HorizontalDivider()
        }
        if (lookupError != null) {
            Text(
                text = "Diagnostic: $lookupError",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}
