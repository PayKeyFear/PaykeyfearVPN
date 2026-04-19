package com.paykeyfear.vpn.ui.screens.home

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.service.PaykeyfearVpnService
import com.paykeyfear.vpn.viewmodel.HomeEvent
import com.paykeyfear.vpn.viewmodel.HomeViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val pendingConfig =
        (event as? HomeEvent.RequestVpnPermissionThenConnect)?.config

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && pendingConfig != null) {
                startVpn(context, pendingConfig)
            }
            viewModel.consumeEvent()
        }

    LaunchedEffect(event) {
        when (val current = event) {
            is HomeEvent.RequestVpnPermissionThenConnect -> {
                val intent = VpnService.prepare(context)
                if (intent != null) {
                    permissionLauncher.launch(intent)
                } else {
                    startVpn(context, current.config)
                    viewModel.consumeEvent()
                }
            }
            HomeEvent.StopTunnel -> {
                stopVpn(context)
                viewModel.consumeEvent()
            }
            null -> Unit
        }
    }

    // Surface tunnel errors once each. The controller keeps the Error
    // state until the next start(), so we key on the message to avoid
    // re-showing the same snackbar on every recomposition.
    val errorMessage = (state.tunnelState as? TunnelState.Error)?.message
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbar.showSnackbar(errorMessage)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text(state.statusLabel, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                state.selected?.let {
                    Text(
                        "${it.displayName} • ${it.protocol.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } ?: run {
                    if (!state.hasAnyServer) {
                        Text(
                            stringResource(R.string.no_servers),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (state.isConnected) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(
                            stringResource(R.string.stats_down, formatBytes(state.stats.rxBytes)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.stats_up, formatBytes(state.stats.txBytes)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = viewModel::toggle,
                    enabled = state.selected != null || state.isConnected,
                ) {
                    Text(
                        stringResource(if (state.isConnected) R.string.disconnect else R.string.connect),
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble() / 1024
    var idx = 0
    while (value >= 1024 && idx < units.lastIndex) {
        value /= 1024
        idx++
    }
    return "%.1f %s".format(value, units[idx])
}

private fun startVpn(context: android.content.Context, config: ConnectionConfig) {
    context.startForegroundService(PaykeyfearVpnService.buildStartIntent(context, config))
}

private fun stopVpn(context: android.content.Context) {
    context.startService(PaykeyfearVpnService.buildStopIntent(context))
}
