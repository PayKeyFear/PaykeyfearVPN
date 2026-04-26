package com.paykeyfear.vpn.ui.screens.home

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.service.PaykeyfearVpnService
import com.paykeyfear.vpn.ui.theme.AccentGreen
import com.paykeyfear.vpn.ui.theme.AccentGreenDim
import com.paykeyfear.vpn.ui.theme.AmberColor
import com.paykeyfear.vpn.ui.theme.Blue
import com.paykeyfear.vpn.ui.theme.BlueDim
import com.paykeyfear.vpn.ui.theme.BorderColor
import com.paykeyfear.vpn.ui.theme.DangerColor
import com.paykeyfear.vpn.ui.theme.DangerDim
import com.paykeyfear.vpn.ui.theme.SurfaceBg
import com.paykeyfear.vpn.ui.theme.SurfaceCard
import com.paykeyfear.vpn.ui.theme.SurfaceCard2
import com.paykeyfear.vpn.ui.theme.TextMuted
import com.paykeyfear.vpn.ui.theme.TextPrimary
import com.paykeyfear.vpn.viewmodel.HomeEvent
import com.paykeyfear.vpn.viewmodel.HomeViewModel
import com.paykeyfear.vpn.viewmodel.ServersViewModel
import com.paykeyfear.vpn.viewmodel.SplitTunnelViewModel
import kotlinx.coroutines.delay

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
    var showErrorSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val errorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    val errorMessage = (state.tunnelState as? TunnelState.Error)?.message
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) showErrorSheet = true
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = false
    }

    Scaffold(
        containerColor = SurfaceBg,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = state.statusLabel,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "status_label",
            ) { label ->
                Text(label, style = MaterialTheme.typography.bodyLarge, color = TextMuted)
            }

            Spacer(Modifier.height(40.dp))

            ConnectCircle(
                state = state.tunnelState,
                enabled = state.selected != null || state.isConnected,
                onClick = viewModel::toggle,
            )

            Spacer(Modifier.height(12.dp))

            // Session timer
            AnimatedVisibility(
                visible = state.isConnected,
                enter = fadeIn(tween(300)) + expandVertically(),
                exit = fadeOut(tween(300)) + shrinkVertically(),
            ) {
                val connectedState = state.tunnelState as? TunnelState.Connected
                if (connectedState != null) {
                    SessionTimer(connectedAtMs = connectedState.connectedAtEpochMs)
                }
            }

            Spacer(Modifier.height(28.dp))

            // Server card
            state.selected?.let { server ->
                ServerInfoCard(
                    server = server,
                    onClick = { if (state.hasAnyServer) showServerPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: run {
                if (state.hasAnyServer) {
                    ServerInfoCard(
                        server = null,
                        onClick = { showServerPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        stringResource(R.string.no_servers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                }
            }

            // Stats
            AnimatedVisibility(
                visible = state.isConnected,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    StatisticsCard(
                        downloadBytes = state.stats.rxBytes,
                        uploadBytes = state.stats.txBytes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

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
                containerColor = SurfaceCard,
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

        if (showErrorSheet && errorMessage != null) {
            ModalBottomSheet(
                onDismissRequest = { showErrorSheet = false },
                sheetState = errorSheetState,
                containerColor = SurfaceCard,
            ) {
                ErrorSheet(
                    message = errorMessage,
                    onRetry = {
                        showErrorSheet = false
                        viewModel.toggle()
                    },
                    onPickServer = {
                        showErrorSheet = false
                        showServerPicker = true
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionTimer(connectedAtMs: Long) {
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(connectedAtMs) {
        while (true) {
            elapsed = (System.currentTimeMillis() - connectedAtMs) / 1000L
            delay(1000L)
        }
    }
    val h = elapsed / 3600
    val m = (elapsed % 3600) / 60
    val s = elapsed % 60
    Text(
        text = "%02d:%02d:%02d".format(h, m, s),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
        ),
        color = AccentGreen,
    )
}

@Composable
private fun ConnectCircle(
    state: TunnelState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Spec: project_connect_button_animation.md.
    // Container is fixed at 240 dp + a halo around it for the pulse rings —
    // the button itself is exactly 240 dp in every state so there is no
    // layout shift between OFF/CONNECTING/ON/DISCONNECTING. All animated
    // layers (decorative ring, rotating arc, pulse rings) are drawn in a
    // Compose Canvas which never consumes pointer events.
    val isConnecting = state == TunnelState.Connecting || state == TunnelState.Disconnecting
    val isConnected = state is TunnelState.Connected

    val ringColor = when {
        isConnected -> AccentGreen
        isConnecting -> AmberColor
        else -> BorderColor
    }
    val animatedRingColor by animateColorAsState(ringColor, tween(600), label = "ring_color")

    val infiniteTransition = rememberInfiniteTransition(label = "connect_anim")

    val arcRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "arc_rotation",
    )

    val buttonGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse),
        label = "btn_glow",
    )

    // Pulse ring 1 — same size as the button, scales up to 1.15× while
    // fading to 0 alpha. 2.4 s ease-out, infinite.
    val pulse1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseOut), RepeatMode.Restart),
        label = "pulse1_scale",
    )
    val pulse1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseOut), RepeatMode.Restart),
        label = "pulse1_alpha",
    )
    // Pulse ring 2 — same as ring 1 but starts 600 ms later and reaches 1.28×.
    val pulse2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            tween(2400, delayMillis = 600, easing = EaseOut),
            RepeatMode.Restart,
        ),
        label = "pulse2_scale",
    )
    val pulse2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(2400, delayMillis = 600, easing = EaseOut),
            RepeatMode.Restart,
        ),
        label = "pulse2_alpha",
    )

    val buttonAlpha = if (isConnecting) buttonGlowAlpha else 1f

    // Outer container is bigger than the button so the pulse rings (max 1.28×)
    // stay inside the layout box and don't get clipped or trigger a layout
    // shift in the parent column.
    Box(
        modifier = modifier.size(320.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Pulse rings — no pointer modifiers, touch-transparent by default.
        if (isConnected) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val strokeWidth = 2.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension / 2f - strokeWidth / 2f
                drawCircle(
                    color = AccentGreen.copy(alpha = pulse1Alpha),
                    radius = baseRadius * pulse1Scale,
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
                drawCircle(
                    color = AccentGreen.copy(alpha = pulse2Alpha),
                    radius = baseRadius * pulse2Scale,
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
            }
        }

        // Outer decorative ring + (optional) spinning arc. Drawn on a
        // canvas that's slightly bigger than the button so the ring sits
        // ~6 dp outside the circle edge and stays visible.
        Canvas(modifier = Modifier.size(256.dp)) {
            val strokeWidth = 3.dp.toPx()
            val radius = size.minDimension / 2f - strokeWidth / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            when {
                isConnecting -> {
                    drawCircle(
                        color = animatedRingColor.copy(alpha = 0.4f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth),
                    )
                    drawArc(
                        color = animatedRingColor,
                        startAngle = arcRotation,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - strokeWidth,
                            size.height - strokeWidth,
                        ),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                isConnected -> {
                    drawCircle(
                        color = animatedRingColor.copy(alpha = 0.8f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth),
                    )
                }
                else -> {
                    // Dashed gray ring (6/4 dash pattern, ~0.5 alpha) — uses
                    // TextMuted as the base so it actually shows up against
                    // the dark surface; BorderColor was too close to SurfaceBg.
                    drawCircle(
                        color = TextMuted.copy(alpha = 0.5f),
                        radius = radius,
                        center = center,
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                                0f,
                            ),
                        ),
                    )
                }
            }
        }

        // Button circle.
        val btnBrush = when {
            isConnected -> Brush.radialGradient(
                listOf(
                    Color(0xFF1A4D38),
                    Color(0xFF0D2B20),
                ),
            )
            isConnecting -> Brush.radialGradient(
                listOf(
                    AmberColor.copy(alpha = 0.7f),
                    AmberColor.copy(alpha = 0.3f),
                ),
            )
            else -> Brush.radialGradient(
                listOf(
                    SurfaceCard2,
                    SurfaceBg,
                ),
            )
        }
        Box(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer(alpha = buttonAlpha)
                .clip(CircleShape)
                .background(btnBrush)
                .clickable(
                    enabled = enabled && !isConnecting,
                    indication = ripple(bounded = true),
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isConnected,
                transitionSpec = {
                    fadeIn(tween(400)) + scaleIn(tween(400), 0.8f) togetherWith
                        fadeOut(tween(250)) + scaleOut(tween(250), 0.8f)
                },
                label = "lock_icon",
            ) { connected ->
                Icon(
                    imageVector = if (connected) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = when {
                        connected -> AccentGreen
                        isConnecting -> AmberColor
                        else -> TextMuted
                    },
                    modifier = Modifier.size(52.dp),
                )
            }
        }
    }
}

@Composable
private fun ServerInfoCard(
    server: ConnectionConfig?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable(
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Globe icon in blue dim circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlueDim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Language,
                    contentDescription = null,
                    tint = Blue,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server?.displayName ?: stringResource(R.string.servers_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                if (server != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${server.protocol.displayName} · ${maskIp("${server.endpoint.host}:${server.endpoint.port}")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = TextMuted,
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF1A2840), Color(0xFF131826))),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatItem(
                icon = Icons.Filled.ArrowDownward,
                value = formatBytes(downloadBytes),
                tint = AccentGreen,
                label = "Download",
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(BorderColor),
            )
            StatItem(
                icon = Icons.Filled.ArrowUpward,
                value = formatBytes(uploadBytes),
                tint = Blue,
                label = "Upload",
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    tint: Color,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable(
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentGreenDim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.split_tunnel_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$modeLabel · $subtitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ErrorSheet(
    message: String,
    onRetry: () -> Unit,
    onPickServer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DangerDim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = DangerColor, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Ошибка подключения", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard2)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = DangerColor,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = SurfaceBg),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Повторить попытку", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPickServer,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = DangerDim,
                contentColor = DangerColor,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Выбрать другой сервер", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }
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
            color = TextPrimary,
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
                    .background(if (isSelected) AccentGreenDim else Color.Transparent)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) AccentGreen else TextPrimary,
                    )
                    Text(
                        text = "${server.protocol.displayName} · ${maskIp("${server.endpoint.host}:${server.endpoint.port}")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                if (isSelected) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

fun maskIp(addr: String): String {
    val regex = Regex("""^(\d+\.\d+)\.\d+\.\d+(:\d+)?$""")
    return regex.replace(addr) { mr ->
        "${mr.groupValues[1]}.*.*${mr.groupValues[2]}"
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
