package com.paykeyfear.vpn.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import com.paykeyfear.vpn.data.repository.ConfigRepository
import com.paykeyfear.vpn.service.PaykeyfearVpnService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Starts the selected tunnel after device boot when the user has enabled
 * "Connect on boot". VPN permission must already have been granted in a prior
 * session — the BroadcastReceiver can't show a consent dialog.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var preferences: PreferencesRepository

    @Inject lateinit var repository: ConfigRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val config = BootAutoConnector.resolvePendingConfig(preferences, repository)
                if (config != null) {
                    context.startForegroundService(PaykeyfearVpnService.buildStartIntent(context, config))
                }
            } catch (t: Throwable) {
                Timber.tag("BootReceiver").w(t, "Failed to start tunnel after boot")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
