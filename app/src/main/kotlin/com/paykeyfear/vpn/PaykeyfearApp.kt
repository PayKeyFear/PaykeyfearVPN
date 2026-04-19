package com.paykeyfear.vpn

import android.app.Application
import com.paykeyfear.vpn.core.logging.VpnLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaykeyfearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VpnLogger.install(debug = BuildConfig.DEBUG)
    }
}
