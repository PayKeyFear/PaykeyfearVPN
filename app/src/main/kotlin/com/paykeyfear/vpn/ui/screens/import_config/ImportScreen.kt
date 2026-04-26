package com.paykeyfear.vpn.ui.screens.import_config

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.ui.theme.AccentGreen
import com.paykeyfear.vpn.ui.theme.AccentGreenDim
import com.paykeyfear.vpn.ui.theme.AwgGreen
import com.paykeyfear.vpn.ui.theme.Blue
import com.paykeyfear.vpn.ui.theme.BlueDim
import com.paykeyfear.vpn.ui.theme.BorderColor
import com.paykeyfear.vpn.ui.theme.DangerColor
import com.paykeyfear.vpn.ui.theme.SurfaceBg
import com.paykeyfear.vpn.ui.theme.SurfaceCard
import com.paykeyfear.vpn.ui.theme.SurfaceCard2
import com.paykeyfear.vpn.ui.theme.TextMuted
import com.paykeyfear.vpn.ui.theme.TextPrimary
import com.paykeyfear.vpn.viewmodel.ConfigPreview
import com.paykeyfear.vpn.viewmodel.ImportViewModel

@Composable
fun ImportScreen(viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadFromUri(context, uri, viewModel)
    }
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotBlank() }?.let { scanned ->
            viewModel.onTextChanged(scanned)
            viewModel.onImportClicked()
        }
    }

    fun launchScanner() {
        val opts = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setBeepEnabled(false)
            .setOrientationLocked(true)
            .setCaptureActivity(PortraitCaptureActivity::class.java)
            .setPrompt(context.getString(R.string.import_scan_prompt))
        qrScanner.launch(opts)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchScanner()
    }

    var selectedMethod by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Spacer(Modifier.weight(1f))

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Title
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Импорт конфигурации", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("VLESS · AmneziaWG", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }

            // Method cards
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportMethodCard(
                    icon = Icons.Filled.ContentPaste,
                    title = "Буфер обмена",
                    hint = "Вставить из clipboard",
                    isSelected = selectedMethod == 0,
                    onClick = {
                        selectedMethod = 0
                        readClipboard(context)?.let(viewModel::onTextChanged)
                    },
                )
                ImportMethodCard(
                    icon = Icons.Filled.FolderOpen,
                    title = "Файл",
                    hint = ".conf, .json",
                    isSelected = selectedMethod == 1,
                    onClick = {
                        selectedMethod = 1
                        filePicker.launch(arrayOf("*/*"))
                    },
                )
                ImportMethodCard(
                    icon = Icons.Filled.QrCodeScanner,
                    title = "QR-код",
                    hint = "Сканировать камерой",
                    isSelected = selectedMethod == 2,
                    onClick = {
                        selectedMethod = 2
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            launchScanner()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                )
            }

            // OR divider
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(BorderColor))
                Text("ИЛИ ВСТАВЬТЕ ТЕКСТ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Box(Modifier.weight(1f).height(1.dp).background(BorderColor))
            }

            // TextField
            ConfigTextField(
                value = state.text,
                onValueChange = viewModel::onTextChanged,
                onClear = { viewModel.onTextChanged("") },
            )

            // Config preview
            AnimatedVisibility(
                visible = state.preview != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                state.preview?.let { preview ->
                    ConfigPreviewCard(
                        preview = preview,
                        nameOverride = state.nameOverride ?: preview.displayName,
                        onNameChange = viewModel::onNameOverrideChanged,
                    )
                }
            }

            if (state.isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentGreen, trackColor = SurfaceCard2)
            }

            state.error?.let {
                Text(stringResource(R.string.import_error, it), color = DangerColor, style = MaterialTheme.typography.bodySmall)
            }
            state.importedName?.let {
                Text(stringResource(R.string.import_success, it), color = AccentGreen, style = MaterialTheme.typography.bodySmall)
            }

            // Import button
            val hasText = state.text.isNotBlank() && !state.isImporting
            Button(
                onClick = viewModel::onImportClicked,
                enabled = hasText,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = SurfaceBg,
                    disabledContainerColor = SurfaceCard2,
                    disabledContentColor = TextMuted,
                ),
            ) {
                Text("Импортировать", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ImportMethodCard(
    icon: ImageVector,
    title: String,
    hint: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        if (isSelected) Color(0xFF182A22) else SurfaceCard,
        animationSpec = tween(200),
        label = "card_bg",
    )
    val borderColor by animateColorAsState(
        if (isSelected) AccentGreen else BorderColor,
        animationSpec = tween(200),
        label = "card_border",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // Border overlay via outline
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Transparent),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AccentGreenDim else SurfaceCard2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (isSelected) AccentGreen else TextMuted, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (isSelected) AccentGreen else TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ConfigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        if (isFocused) AccentGreen else BorderColor,
        animationSpec = tween(200),
        label = "tf_border",
    )
    val bgColor by animateColorAsState(
        if (isFocused) SurfaceCard2 else SurfaceCard,
        animationSpec = tween(200),
        label = "tf_bg",
    )

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bgColor)
                .padding(1.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(bgColor),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .onFocusChanged { isFocused = it.isFocused },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp,
                ),
                cursorBrush = SolidColor(AccentGreen),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            "vless://... или полную конфигурацию",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted),
                        )
                    }
                    inner()
                },
            )
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.align(Alignment.TopEnd).size(36.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (value.isNotEmpty()) {
            Text(
                "${value.length} символов",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ConfigPreviewCard(
    preview: ConfigPreview,
    nameOverride: String,
    onNameChange: (String) -> Unit,
) {
    val (chipColor, dimColor) = when (preview.protocol) {
        Protocol.AWG -> AwgGreen to AccentGreenDim
        Protocol.VLESS -> Blue to BlueDim
        Protocol.HYSTERIA2 -> AccentGreen to AccentGreenDim
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(dimColor).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(
                    preview.protocol.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = chipColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.weight(1f))
            Text("${preview.host}:${preview.port}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        OutlinedTextField(
            value = nameOverride,
            onValueChange = onNameChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.import_name_label)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedLabelColor = AccentGreen,
                unfocusedLabelColor = TextMuted,
                cursorColor = AccentGreen,
            ),
        )
    }
}

private fun readClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
}

private fun loadFromUri(context: Context, uri: Uri, viewModel: ImportViewModel) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    }.getOrNull()?.takeIf { it.isNotBlank() }?.let(viewModel::onTextChanged)
}
