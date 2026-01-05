package com.joebad.fastbreak.ui.theme

import androidx.compose.runtime.Composable

/**
 * iOS-specific system UI configuration.
 * iOS automatically manages status bar appearance based on the app's color scheme,
 * so no manual configuration is needed.
 */
@Composable
actual fun ConfigureSystemUi(themeMode: ThemeMode) {
    // No-op: iOS handles this automatically
}
