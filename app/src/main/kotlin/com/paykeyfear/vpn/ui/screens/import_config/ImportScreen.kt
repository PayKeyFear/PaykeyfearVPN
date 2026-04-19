package com.paykeyfear.vpn.ui.screens.import_config

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.viewmodel.ImportViewModel

@Composable
fun ImportScreen(viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) loadFromUri(context, uri, viewModel)
        }

    val qrScanner =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.takeIf { it.isNotBlank() }?.let { scanned ->
                // Treat QR contents as the user's import payload AND
                // immediately kick off the import — without this the
                // happy path requires an extra "Import" button tap that
                // testers consistently miss after scanning.
                viewModel.onTextChanged(scanned)
                viewModel.onImportClicked()
            }
        }

    // Build the scan options once — we re-use them whether the camera
    // permission was already granted or has just been granted via the
    // permission launcher below.
    fun launchScanner() {
        val opts = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setBeepEnabled(false)
            // Lock orientation: ZXing's CaptureActivity defaults to
            // landscape with sensor orientation, which on most phones
            // rotates the device the moment the activity launches and
            // (combined with the missing CAMERA permission) made the
            // scanner appear as a frozen sideways preview.
            .setOrientationLocked(true)
            .setCaptureActivity(PortraitCaptureActivity::class.java)
            .setPrompt(context.getString(R.string.import_scan_prompt))
        qrScanner.launch(opts)
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchScanner()
        }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::onTextChanged,
            modifier = Modifier.fillMaxWidth().height(220.dp),
            label = { Text(stringResource(R.string.import_paste_hint)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { readClipboard(context)?.let(viewModel::onTextChanged) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.import_paste_clipboard)) }
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.import_open_file)) }
        }
        OutlinedButton(
            onClick = {
                // ZXing's embedded CaptureActivity does NOT request the
                // CAMERA permission itself — if we launch it without the
                // permission granted the camera preview silently fails
                // and the user sees a rotated blank screen. Request
                // first, then launch on grant.
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) launchScanner()
                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.import_scan_qr)) }
        Button(
            onClick = viewModel::onImportClicked,
            enabled = state.text.isNotBlank() && !state.isImporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_button))
        }
        state.error?.let {
            Text(
                stringResource(R.string.import_error, it),
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.importedName?.let {
            Text(
                stringResource(R.string.import_success, it),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
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
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }?.let(viewModel::onTextChanged)
}
