package com.joebad.fastbreak

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
expect fun onApplicationStartPlatformSpecific()

@Composable
expect fun ApplySystemBarsColor(color: Color)