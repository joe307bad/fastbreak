package com.joebad.fastbreak.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual object AppUpdatePrompt {
    // App Store ID for Fastbreak (replace with actual ID once published)
    private const val APP_STORE_ID = "6744640701"
    private const val APP_STORE_URL = "https://apps.apple.com/app/id$APP_STORE_ID"

    @OptIn(ExperimentalForeignApi::class)
    actual fun openAppStore() {
        val nsUrl = NSURL.URLWithString(APP_STORE_URL) ?: return
        UIApplication.sharedApplication.openURL(
            url = nsUrl,
            options = emptyMap<Any?, Any>(),
            completionHandler = null
        )
    }
}
