package com.paykeyfear.vpn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.paykeyfear.vpn.config.ConfigParserRegistry
import com.paykeyfear.vpn.config.ConfigSource
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import com.paykeyfear.vpn.data.repository.ConfigRepository
import com.paykeyfear.vpn.ui.navigation.PaykeyfearNavHost
import com.paykeyfear.vpn.ui.theme.PaykeyfearTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var parserRegistry: ConfigParserRegistry

    @Inject lateinit var repository: ConfigRepository

    @Inject lateinit var preferences: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dynamicColor by preferences.dynamicColorEnabled.collectAsState(initial = true)
            PaykeyfearTheme(dynamicColor = dynamicColor) {
                PaykeyfearNavHost()
            }
        }
        handleShareIntent(intent)
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
