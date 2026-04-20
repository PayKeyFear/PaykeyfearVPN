package com.paykeyfear.vpn.ui.screens.home

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.service.PaykeyfearVpnService
import com.paykeyfear.vpn.viewmodel.HomeEvent
import com.paykeyfear.vpn.viewmodel.HomeViewModel
import com.paykeyfear.vpn.viewmodel.ServersViewModel
import com.paykeyfear.vpn.viewmodel.SplitTunnelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSplitTunnelClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    splitTunnelViewModel: SplitTunnelViewModel = hiltViewModel(),
    serversViewModel: ServersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val splitState by splitTunnelViewModel.state.collectAsStateWithLifecycle()
    val serversState by serversViewModel.state.collectAsStateWithLifecycle()

    var showServerPicker by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    val view = LocalView.current
    val isConnected = state.isConnected
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, view)
        window.statusBarColor = if (isConnected) android.graphics.Color.parseColor("#1B5E20") else android.graphics.Color.TRANSPARENT
        controller.isAppearanceLightStatusBars = !isConnected
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
                    protocolColor = protocolColor(server.protocol),
                    onClick = { if (state.hasAnyServer) showServerPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: run {
                if (state.hasAnyServer) {
                    ServerInfoCard(
                        serverName = stringResource(R.string.servers_title),
                        protocol = "",
                        protocolColor = MaterialTheme.colorScheme.outline,
                        onClick = { showServerPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
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

        if (showServerPicker) {
            ModalBottomSheet(
                onDismissRequest = { showServerPicker = false },
                sheetState = sheetState,
            ) {
                ServerPickerSheet(
                    servers = serversState.servers,
                    selectedId = serversState.selectedId,
                    onSelect = { id ->
                        serversViewModel.select(id)
                        showServerPicker = false
                    },
                )
            }
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
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(800, easing = EaseInOut),
        label = "circle_color",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "arc_spin")

    // Дуга крутится плавно
    val arcRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "arc_rotation",
    )

    // Длина дуги пульсирует (40° → 260°) — эффект "набегающей волны"
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = 260f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = EaseInOutCubic),
            RepeatMode.Reverse,
        ),
        label = "arc_sweep",
    )

    // Фоновое кольцо плавно появляется/исчезает
    val ringAlpha by animateFloatAsState(
        targetValue = if (isConnecting) 0.18f else 0.0f,
        animationSpec = tween(600),
        label = "ring_alpha",
    )

    // Иконка: плавная смена через alpha
    val lockAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(500, easing = EaseInOut),
        label = "lock_alpha",
    )
    val lockScale by animateFloatAsState(
        targetValue = if (isConnecting) 0.85f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lock_scale",
    )

    Box(
        modifier = modifier
            .size(360.dp)
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
            val strokeWidth = 8.dp.toPx()

            // Фоновый заполненный круг
            drawCircle(color = animatedColor.copy(alpha = 0.07f))

            if (isConnecting) {
                // Тонкое фоновое кольцо
                drawCircle(
                    color = animatedColor.copy(alpha = ringAlpha),
                    style = Stroke(width = strokeWidth),
                )
                // Движущаяся дуга с переменной длиной — плавнее и живее
                drawArc(
                    color = animatedColor,
                    startAngle = arcRotation - sweepAngle / 2f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            } else {
                // Статичное кольцо
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
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                fadeIn(tween(400, easing = EaseInOut)) +
                    scaleIn(tween(400, easing = EaseInOut), initialScale = 0.75f) togetherWith
                    fadeOut(tween(250)) +
                    scaleOut(tween(250), targetScale = 0.75f)
            },
            label = "lock_icon",
        ) { currentIcon ->
            Icon(
                imageVector = currentIcon,
                contentDescription = null,
                tint = animatedColor,
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer(scaleX = lockScale, scaleY = lockScale),
            )
        }
    }
}

@Composable
private fun protocolColor(protocol: Protocol) = when (protocol) {
    Protocol.AWG -> Color(0xFF4CAF50)
    Protocol.VLESS -> Color(0xFF2196F3)
    Protocol.HYSTERIA2 -> Color(0xFFFF9800)
}

@Composable
private fun ServerInfoCard(
    serverName: String,
    protocol: String,
    protocolColor: Color = MaterialTheme.colorScheme.outline,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = serverName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (protocol.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = protocol,
                        style = MaterialTheme.typography.bodyMedium,
                        color = protocolColor,
                    )
                }
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

@Composable
private fun ServerPickerSheet(
    servers: List<ConnectionConfig>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.servers_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        servers.forEach { server ->
            val isSelected = server.id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = ripple(bounded = true),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onSelect(server.id) },
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = server.protocol.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
