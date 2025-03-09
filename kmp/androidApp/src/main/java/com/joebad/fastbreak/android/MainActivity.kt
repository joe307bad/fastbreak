package com.joebad.fastbreak.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.joebad.fastbreak.App
import com.joebad.fastbreak.createRootComponent
import com.joebad.fastbreak.initFontLoader
import com.joebad.fastbreak.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initFontLoader(applicationContext)

        val rootComponent = createRootComponent()

        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }
            WindowCompat.setDecorFitsSystemWindows(window, false) // Enable edge-to-edge rendering
            AppTheme(isDarkTheme, onToggleTheme = {
                isDarkTheme = !isDarkTheme
            }) {
                App(rootComponent = rootComponent, onToggleTheme = { isDarkTheme = !isDarkTheme })
            }
        }
    }
}