package com.joebad.fastbreak

import Theme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
expect fun onApplicationStartPlatformSpecific()

@Composable
expect fun ApplySystemBarsColor(color: Color)

interface ThemePreference {
    suspend fun saveTheme(theme: Theme)
    suspend fun getTheme(): Theme
}
