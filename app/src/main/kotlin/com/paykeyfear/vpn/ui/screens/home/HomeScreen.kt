package com.paykeyfear.vpn.ui.screens.home

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.service.PaykeyfearVpnService
import com.paykeyfear.vpn.viewmodel.HomeEvent
import com.paykeyfear.vpn.viewmodel.HomeViewModel
import com.paykeyfear.vpn.viewmodel.SplitTunnelViewModel

@Composable
fun HomeScreen(
    onSplitTunnelClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    splitTunnelViewModel: SplitTunnelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val splitState by splitTunnelViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val pendingConfig = (event as? HomeEvent.RequestVpnPermissionThenConnect)?.config

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
                if (intent != null) permissionLauncher.launch(intent)
                else {
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

    val errorMessage = (state.tunnelState as? TunnelState.Error)?.message
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) snackbar.showSnackbar(errorMessage)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = state.statusLabel,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "status_label",
            ) { label ->
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(48.dp))

            // Big animated connect circle
            ConnectCircle(
                state = state.tunnelState,
                enabled = state.selected != null || state.isConnected,
                onClick = viewModel::toggle,
            )

            Spacer(Modifier.height(40.dp))

            // Server info card
            state.selected?.let { server ->
                ServerInfoCard(
                    serverName = server.displayName,
                    protocol = server.protocol.displayName,
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: run {
                if (!state.hasAnyServer) {
                    Text(
                        stringResource(R.string.no_servers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Stats
            AnimatedVisibility(
                visible = state.isConnected,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Spacer(Modifier.height(16.dp))
                StatisticsCard(
                    downloadBytes = state.stats.rxBytes,
                    uploadBytes = state.stats.txBytes,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Split tunnel quick card
            SplitTunnelCard(
                mode = splitState.mode,
                selectedCount = splitState.selected.size,
                onClick = onSplitTunnelClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConnectCircle(
    state: TunnelState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnecting = state == TunnelState.Connecting || state == TunnelState.Disconnecting
    val isConnected = state is TunnelState.Connected
    val isError = state is TunnelState.Error

    val targetColor = when {
        isConnected -> Color(0xFF4CAF50)
        isConnecting -> MaterialTheme.colorScheme.tertiary
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val animatedColor by animateColorAsState(targetColor, tween(600), label = "circle_color")

    val infiniteTransition = rememberInfiniteTransition(label = "arc_spin")
    val arcRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "arc_rotation",
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val inset = strokeWidth / 2f

            // Faint background fill
            drawCircle(color = animatedColor.copy(alpha = 0.08f))

            if (isConnecting) {
                // Spinning arc — 180° sweep, rotates continuously
                drawArc(
                    color = animatedColor.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                )
                drawArc(
                    color = animatedColor,
                    startAngle = arcRotation,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            } else {
                // Static ring
                drawCircle(
                    color = animatedColor,
                    style = Stroke(width = strokeWidth),
                )
            }
        }

        val icon = when {
            isConnected -> Icons.Filled.Lock
            isError -> Icons.Filled.Error
            else -> Icons.Filled.LockOpen
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = animatedColor,
            modifier = Modifier.size(52.dp),
        )
    }
}

@Composable
private fun ServerInfoCard(
    serverName: String,
    protocol: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = serverName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = protocol,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun StatisticsCard(
    downloadBytes: Long,
    uploadBytes: Long,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatisticItem(Icons.Filled.ArrowDownward, formatBytes(downloadBytes), Color(0xFF4CAF50))
            StatisticItem(Icons.Filled.ArrowUpward, formatBytes(uploadBytes), Color(0xFF2196F3))
        }
    }
}

@Composable
private fun StatisticItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble() / 1024
    var idx = 0
    while (value >= 1024 && idx < units.lastIndex) { value /= 1024; idx++ }
    return "%.1f %s".format(value, units[idx])
}

@Composable
private fun SplitTunnelCard(
    mode: SplitTunnelMode,
    selectedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modeLabel = when (mode) {
        SplitTunnelMode.Off -> stringResource(R.string.split_mode_off)
        SplitTunnelMode.Allowlist -> stringResource(R.string.split_mode_allow)
        SplitTunnelMode.Denylist -> stringResource(R.string.split_mode_deny)
    }
    val subtitle = when (mode) {
        SplitTunnelMode.Off -> stringResource(R.string.split_desc_off)
        SplitTunnelMode.Allowlist -> "$selectedCount ${stringResource(R.string.split_mode_allow).lowercase()}"
        SplitTunnelMode.Denylist -> "$selectedCount ${stringResource(R.string.split_mode_deny).lowercase()}"
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.split_tunnel_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$modeLabel · $subtitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun startVpn(context: android.content.Context, config: ConnectionConfig) {
    context.startForegroundService(PaykeyfearVpnService.buildStartIntent(context, config))
}

private fun stopVpn(context: android.content.Context) {
    context.startService(PaykeyfearVpnService.buildStopIntent(context))
}
