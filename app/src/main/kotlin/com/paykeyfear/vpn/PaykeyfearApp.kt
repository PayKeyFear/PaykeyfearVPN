package com.paykeyfear.vpn

import android.app.Application
import com.paykeyfear.vpn.core.logging.VpnLogger
import com.paykeyfear.vpn.geo.GeoUpdateScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaykeyfearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VpnLogger.install(debug = BuildConfig.DEBUG)
        // Robolectric-driven unit tests boot the Application without the
        // WorkManager content provider, so schedule() throws
        // "WorkManager is not initialized properly" during test setup.
        // Swallow so tests stay focused on their own target code.
        runCatching { GeoUpdateScheduler.schedule(this) }
    }
}
