package com.joebad.fastbreak

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.joebad.fastbreak.ui.theme.AppTheme

fun MainViewController() = ComposeUIViewController {
    var isDarkTheme by remember { mutableStateOf(false) }
    val fontLoader = createFontLoader()
    val myFont = fontLoader.loadFont("CodeBold.otf")
    AppTheme(isDarkTheme, onToggleTheme = { isDarkTheme = !isDarkTheme }) {
        App(rootComponent = createRootComponent(), onToggleTheme = { isDarkTheme = !isDarkTheme })
    }
}