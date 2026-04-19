package com.paykeyfear.vpn.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paykeyfear.vpn.R
import com.paykeyfear.vpn.core.logging.LogEntry
import com.paykeyfear.vpn.core.logging.VpnLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen() {
    val entries by VpnLogger.entries.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.logs_subtitle),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { VpnLogger.clear() }) {
                Text(stringResource(R.string.logs_clear))
            }
        }
        HorizontalDivider()
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.logs_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            // Newest entries first — logs are appended chronologically.
            val reversed = entries.asReversed()
            LazyColumn(Modifier.fillMaxSize()) {
                items(reversed, key = { it.epochMs.toString() + it.message.hashCode() }) { entry ->
                    LogRow(entry)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            text = "${formatTime(entry.epochMs)}  ${priorityLabel(entry.priority)}  ${entry.tag ?: "-"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        entry.throwable?.let { t ->
            Text(
                text = t.stackTraceToString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

private fun formatTime(ms: Long): String = TIME_FORMAT.format(Date(ms))

private fun priorityLabel(p: Int): String =
    when (p) {
        2 -> "V"
        3 -> "D"
        4 -> "I"
        5 -> "W"
        6 -> "E"
        7 -> "A"
        else -> "?"
    }
