package com.joebad.fastbreak

import android.app.Application
import android.database.CursorWindow
import io.sentry.android.core.SentryAndroid

class FastbreakApplication : Application() {
    override fun onCreate() {
        // MLB report card and other large charts exceed the default ~2MB CursorWindow.
        // Must run before any large chart_cache rows are read.
        increaseCursorWindowSize()

        super.onCreate()

        SentryAndroid.init(this) { options ->
            options.dsn = "https://7fe7a36cac4f06f2c9be1fa251b27157@o170588.ingest.us.sentry.io/4509794765766656"
            options.isDebug = BuildConfig.DEBUG
            options.tracesSampleRate = 1.0
            options.isEnableAutoSessionTracking = true
            options.environment = if (BuildConfig.DEBUG) "development" else "production"
        }
    }

    private fun increaseCursorWindowSize() {
        val sizeBytes = 20 * 1024 * 1024
        try {
            val method = CursorWindow::class.java.getDeclaredMethod("setCursorWindowSize", Int::class.javaPrimitiveType)
            method.invoke(null, sizeBytes)
        } catch (_: Exception) {
            try {
                val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
                field.isAccessible = true
                field.set(null, sizeBytes)
            } catch (e: Exception) {
                println("⚠ Could not increase SQLite CursorWindow size: ${e.message}")
            }
        }
    }
}
