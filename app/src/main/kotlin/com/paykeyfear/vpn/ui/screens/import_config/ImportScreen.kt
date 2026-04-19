package com.paykeyfear.vpn.ui.screens.import_config

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
            result.contents?.takeIf { it.isNotBlank() }?.let(viewModel::onTextChanged)
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
                val opts = ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .setPrompt(context.getString(R.string.import_scan_prompt))
                qrScanner.launch(opts)
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
