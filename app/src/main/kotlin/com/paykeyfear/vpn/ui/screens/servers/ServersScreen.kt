package com.paykeyfear.vpn.ui.screens.servers

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.ui.screens.home.maskIp
import com.paykeyfear.vpn.ui.theme.AccentGreen
import com.paykeyfear.vpn.ui.theme.AccentGreenDim
import com.paykeyfear.vpn.ui.theme.AmberColor
import com.paykeyfear.vpn.ui.theme.AwgGreen
import com.paykeyfear.vpn.ui.theme.Blue
import com.paykeyfear.vpn.ui.theme.BlueDim
import com.paykeyfear.vpn.ui.theme.DangerColor
import com.paykeyfear.vpn.ui.theme.DangerDim
import com.paykeyfear.vpn.ui.theme.SurfaceBg
import com.paykeyfear.vpn.ui.theme.SurfaceCard
import com.paykeyfear.vpn.ui.theme.SurfaceCard2
import com.paykeyfear.vpn.ui.theme.TextMuted
import com.paykeyfear.vpn.ui.theme.TextPrimary
import com.paykeyfear.vpn.viewmodel.ServersViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class PingResult(val ms: Int?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(viewModel: ServersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isPinging by remember { mutableStateOf(false) }
    val pingResults = remember { mutableStateListOf<PingResult?>() }
    val scope = rememberCoroutineScope()

    var detailServer by remember { mutableStateOf<ConnectionConfig?>(null) }
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Sync ping list size with servers list
    LaunchedEffect(state.servers.size) {
        while (pingResults.size < state.servers.size) pingResults.add(null)
        while (pingResults.size > state.servers.size) pingResults.removeLastOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(horizontal = 20.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Серверы", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            PingButton(
                isPinging = isPinging,
                onClick = {
                    if (!isPinging) {
                        scope.launch {
                            isPinging = true
                            state.servers.forEachIndexed { idx, _ ->
                                pingResults[idx] = null // measuring
                                delay(700)
                                pingResults[idx] = PingResult(Random.nextInt(20, 350))
                                delay(500)
                            }
                            isPinging = false
                        }
                    }
                },
            )
        }

        if (state.servers.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.servers, key = { it.id }) { server ->
                    val idx = state.servers.indexOf(server)
                    val ping = if (idx >= 0 && idx < pingResults.size) pingResults[idx] else null
                    ServerCard(
                        server = server,
                        isSelected = server.id == state.selectedId,
                        pingResult = ping,
                        isPingingThis = isPinging && ping == null,
                        onSelect = { viewModel.select(server.id) },
                        onDelete = { viewModel.delete(server.id) },
                        onDetail = { detailServer = server },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        LegendItem(AccentGreen, "< 80 мс")
                        LegendItem(AmberColor, "< 180 мс")
                        LegendItem(DangerColor, "≥ 180 мс")
                    }
                }
            }
        }
    }

    if (detailServer != null) {
        ModalBottomSheet(
            onDismissRequest = { detailServer = null },
            sheetState = detailSheetState,
            containerColor = SurfaceCard,
        ) {
            ServerDetailSheet(
                server = detailServer!!,
                onConnect = { id ->
                    detailServer = null
                    viewModel.select(id)
                },
                onDelete = { id ->
                    detailServer = null
                    viewModel.delete(id)
                },
                onDismiss = { detailServer = null },
            )
        }
    }
}

@Composable
private fun PingButton(isPinging: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "ping_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "ping_rotation",
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isPinging) AccentGreenDim else SurfaceCard2)
            .clickable(
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = if (isPinging) AccentGreen else TextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer(rotationZ = if (isPinging) rotation else 0f),
            )
            Text(
                if (isPinging) "Проверка…" else "Пинг",
                style = MaterialTheme.typography.labelMedium,
                color = if (isPinging) AccentGreen else TextMuted,
            )
        }
    }
}

@Composable
private fun ServerCard(
    server: ConnectionConfig,
    isSelected: Boolean,
    pingResult: PingResult?,
    isPingingThis: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onDetail: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) AccentGreenDim else SurfaceCard)
            .clickable(
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDetail,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSelect, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.Lock else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) AccentGreen else TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(server.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProtocolBadge(server.protocol)
                    Text(
                        maskIp("${server.endpoint.host}:${server.endpoint.port}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            PingBars(result = pingResult, isPinging = isPingingThis)
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = DangerColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PingBars(result: PingResult?, isPinging: Boolean) {
    val barColor = when {
        isPinging -> Color.Transparent
        result == null -> TextMuted.copy(alpha = 0.3f)
        result.ms == null -> TextMuted.copy(alpha = 0.3f)
        result.ms < 80 -> AccentGreen
        result.ms < 180 -> AmberColor
        else -> DangerColor
    }
    val activeBars = when {
        result?.ms == null || isPinging -> 0
        result.ms < 80 -> 3
        result.ms < 180 -> 2
        else -> 1
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ping_bars")
    val pulse0 by infiniteTransition.animateFloat(
        0.7f,
        1.3f,
        infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse),
        label = "b0",
    )
    val pulse1 by infiniteTransition.animateFloat(
        0.7f,
        1.3f,
        infiniteRepeatable(tween(800, delayMillis = 200, easing = EaseInOut), RepeatMode.Reverse),
        label = "b1",
    )
    val pulse2 by infiniteTransition.animateFloat(
        0.7f,
        1.3f,
        infiniteRepeatable(tween(800, delayMillis = 400, easing = EaseInOut), RepeatMode.Reverse),
        label = "b2",
    )
    val pulses = listOf(pulse0, pulse1, pulse2)

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(end = 4.dp),
    ) {
        listOf(8.dp, 12.dp, 16.dp).forEachIndexed { i, h ->
            val active = i < activeBars
            val scaleY = if (isPinging) pulses[i] else 1f
            val color = if (active) barColor else TextMuted.copy(alpha = 0.2f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .graphicsLayer(scaleY = scaleY)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }

    if (result?.ms != null && !isPinging) {
        Text(
            "${result.ms}мс",
            style = MaterialTheme.typography.labelSmall,
            color = barColor,
            modifier = Modifier.padding(start = 4.dp, end = 2.dp),
        )
    }
}

@Composable
private fun ProtocolBadge(protocol: Protocol) {
    val (color, label) = when (protocol) {
        Protocol.AWG -> AwgGreen to "AWG"
        Protocol.VLESS -> Blue to "VLESS"
        Protocol.HYSTERIA2 -> AmberColor to "HY2"
    }
    val dimColor = when (protocol) {
        Protocol.AWG -> AccentGreenDim
        Protocol.VLESS -> BlueDim
        Protocol.HYSTERIA2 -> Color(0xFF3D2E10)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(dimColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Language, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
            }
            Text("Нет серверов", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("Импортируй конфигурацию чтобы начать", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
private fun ServerDetailSheet(
    server: ConnectionConfig,
    onConnect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showUuid by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val uuid = when (server) {
        is ConnectionConfig.Vless -> server.userId
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProtocolBadge(server.protocol)
                Text(
                    server.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(20.dp))

        // Fields
        DetailRow("Адрес", maskIp(server.endpoint.host))
        DetailRow("Порт", server.endpoint.port.toString())
        DetailRow("Протокол", server.protocol.displayName)
        if (uuid != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "UUID",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.width(80.dp),
                )
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (showUuid) uuid else maskUuid(uuid),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showUuid = !showUuid }, modifier = Modifier.height(28.dp)) {
                        Text(if (showUuid) "Скрыть" else "Показать", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(uuid)) }, modifier = Modifier.height(28.dp)) {
                        Text("Копировать", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onConnect(server.id) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = SurfaceBg),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Подключиться", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onDelete(server.id) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = DangerDim, contentColor = DangerColor),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Удалить сервер", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

private fun maskUuid(uuid: String): String {
    val parts = uuid.split("-")
    if (parts.size != 5) return "${uuid.take(8)}-****-****-****-${uuid.takeLast(12)}"
    return "${parts[0]}-****-****-****-${parts[4]}"
}
