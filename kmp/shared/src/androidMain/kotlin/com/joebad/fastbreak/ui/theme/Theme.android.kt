package com.joebad.fastbreak.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Android-specific function to configure system UI (status bar and navigation bar)
 * based on the current theme mode.
 */
@Composable
actual fun ConfigureSystemUi(themeMode: ThemeMode) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)

            // Set status bar appearance based on theme
            // Light mode = dark icons (true), Dark mode = light icons (false)
            insetsController.isAppearanceLightStatusBars = themeMode == ThemeMode.LIGHT
            insetsController.isAppearanceLightNavigationBars = themeMode == ThemeMode.LIGHT
        }
    }
}
