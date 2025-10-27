package com.example.kmpapp

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.example.kmpapp.navigation.RootComponent
import com.example.kmpapp.ui.theme.SystemThemeDetector
import com.example.kmpapp.ui.theme.ThemeRepository
import com.russhwolf.settings.Settings
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val settings = Settings()
    val themeRepository = ThemeRepository(
        settings = settings,
        systemThemeDetector = SystemThemeDetector()
    )

    val rootComponent = RootComponent(
        componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
        themeRepository = themeRepository
    )

    return ComposeUIViewController {
        App(rootComponent)
    }
}
