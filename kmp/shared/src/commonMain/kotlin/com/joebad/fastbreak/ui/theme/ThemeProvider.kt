package com.joebad.fastbreak.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun AppTheme(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = rememberAppColors(isDarkTheme)

    CompositionLocalProvider(LocalColors provides colors) {
        content()
    }
}
