package com.paykeyfear.vpn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.paykeyfear.vpn.config.ConfigParserRegistry
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import com.paykeyfear.vpn.data.repository.ConfigRepository
import com.paykeyfear.vpn.ui.navigation.PaykeyfearNavHost
import com.paykeyfear.vpn.ui.theme.PaykeyfearTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var parserRegistry: ConfigParserRegistry

    @Inject lateinit var repository: ConfigRepository

    @Inject lateinit var preferences: PreferencesRepository

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaykeyfearTheme {
                PaykeyfearNavHost()
            }
        }
        requestNotificationPermission()
        handleShareIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        if (!data.startsWith("vless://") && !data.startsWith("hysteria2://") && !data.startsWith("vpn://")) return
        lifecycleScope.launch {
            runCatching {
                val parsed = parserRegistry.parse(ConfigSource.Text("shared", data))
                repository.upsert(parsed)
                preferences.setSelectedConfigId(parsed.id)
            }.onFailure { Timber.tag("MainActivity").w(it, "Failed to import shared URI") }
        }
    }
}
