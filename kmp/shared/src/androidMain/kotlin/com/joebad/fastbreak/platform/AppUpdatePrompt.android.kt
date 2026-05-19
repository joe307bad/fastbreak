package com.joebad.fastbreak.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

// Global context holder (reuses the same context as UrlLauncher)
private var applicationContext: Context? = null

fun initializeAppUpdatePrompt(context: Context) {
    applicationContext = context.applicationContext
}

actual object AppUpdatePrompt {
    // Package name for Play Store deep link
    private const val PACKAGE_NAME = "com.joebad.fastbreak"
    private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$PACKAGE_NAME"
    private const val MARKET_URI = "market://details?id=$PACKAGE_NAME"

    actual fun openAppStore() {
        val context = applicationContext
            ?: throw IllegalStateException("AppUpdatePrompt not initialized. Call initializeAppUpdatePrompt() first.")

        // Try to open Play Store app first, fall back to web browser
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_URI)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (e: Exception) {
            // Fall back to web URL if Play Store app not installed
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
