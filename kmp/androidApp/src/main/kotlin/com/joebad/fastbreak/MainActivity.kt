package com.joebad.fastbreak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.example.kmpapp.App
import com.example.kmpapp.navigation.RootComponent
import com.example.kmpapp.ui.theme.SystemThemeDetector
import com.example.kmpapp.ui.theme.ThemeRepository
import com.russhwolf.settings.Settings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = Settings()
        val themeRepository = ThemeRepository(
            settings = settings,
            systemThemeDetector = SystemThemeDetector()
        )

        val rootComponent = RootComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            themeRepository = themeRepository
        )

        setContent {
            App(rootComponent)
        }
    }
}
