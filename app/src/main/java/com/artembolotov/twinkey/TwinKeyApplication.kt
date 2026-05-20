package com.artembolotov.twinkey

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

class TwinKeyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val apiKey = BuildConfig.APPMETRICA_API_KEY
        if (apiKey.isNotEmpty()) {
            val config = AppMetricaConfig.newConfigBuilder(apiKey).build()
            AppMetrica.activate(this, config)
            AppMetrica.enableActivityAutoTracking(this)
        }
    }
}
