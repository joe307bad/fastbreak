package com.joebad.fastbreak

import androidx.compose.ui.text.font.FontFamily

interface FontLoader {
    fun loadFont(fontName: String): FontFamily
}

expect fun createFontLoader(): FontLoader