package com.artembolotov.twinkey

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

class TwinKeyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val config = AppMetricaConfig.newConfigBuilder("***REMOVED***").build()
        AppMetrica.activate(this, config)
        AppMetrica.enableActivityAutoTracking(this)
    }
}
