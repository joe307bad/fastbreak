package com.joebad.fastbreak.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

// Global context holder
private var applicationContext: Context? = null

fun initializeUrlLauncher(context: Context) {
    applicationContext = context.applicationContext
}

actual object UrlLauncher {
    actual fun openUrl(url: String) {
        val context = applicationContext
            ?: throw IllegalStateException("UrlLauncher not initialized. Call initializeUrlLauncher() first.")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
