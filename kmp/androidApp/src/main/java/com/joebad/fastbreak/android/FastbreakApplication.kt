package com.joebad.fastbreak.android

import android.app.Application
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User

class FastbreakApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Sentry
        SentryAndroid.init(this) { options ->
            // DSN is automatically read from AndroidManifest.xml
            options.dsn = "https://7fe7a36cac4f06f2c9be1fa251b27157@o170588.ingest.us.sentry.io/4509794765766656"
            
            // Set environment
            options.environment = if (BuildConfig.DEBUG) "development" else "production"
            
            // Set release
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}"
            
            // Sample rate for transactions (performance monitoring)
            options.tracesSampleRate = 1.0
            
            // Enable debug logging in debug builds
            options.isDebug = BuildConfig.DEBUG
            
            // Enable automatic breadcrumbs for user interactions
            options.isEnableUserInteractionBreadcrumbs = true
            
            // Attach screenshots for crashes
            options.isAttachScreenshot = true
            
            // Attach view hierarchy for crashes
            options.isAttachViewHierarchy = true
            
            // Send default PII (personally identifiable information)
            options.isSendDefaultPii = true
        }
        
        // Set user context (optional)
        Sentry.setUser(User().apply {
            id = "android-user"
        })
    }
}