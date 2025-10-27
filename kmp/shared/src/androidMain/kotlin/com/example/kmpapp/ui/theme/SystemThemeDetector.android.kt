package com.example.kmpapp.ui.theme

import android.content.res.Configuration
import android.content.res.Resources

actual class SystemThemeDetector {
    actual fun isSystemInDarkMode(): Boolean {
        val nightModeFlags = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}
