package com.joebad.fastbreak

import android.app.Application
import io.sentry.android.core.SentryAndroid

class FastbreakApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        SentryAndroid.init(this) { options ->
            options.dsn = "https://7fe7a36cac4f06f2c9be1fa251b27157@o170588.ingest.us.sentry.io/4509794765766656"
            options.isDebug = BuildConfig.DEBUG
            options.tracesSampleRate = 1.0
            options.isEnableAutoSessionTracking = true
            options.environment = if (BuildConfig.DEBUG) "development" else "production"
        }
    }
}
