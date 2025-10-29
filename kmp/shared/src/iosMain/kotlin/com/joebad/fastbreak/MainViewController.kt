package com.joebad.fastbreak

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.joebad.fastbreak.navigation.RootComponent
import com.joebad.fastbreak.ui.theme.SystemThemeDetector
import com.joebad.fastbreak.ui.theme.ThemeRepository
import com.russhwolf.settings.Settings
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val settings = Settings()
    val themeRepository = ThemeRepository(
        settings = settings,
        systemThemeDetector = SystemThemeDetector()
    )
    val registryRepository = com.joebad.fastbreak.data.repository.RegistryRepository(settings)
    val chartDataRepository = com.joebad.fastbreak.data.repository.ChartDataRepository(settings)

    val rootComponent = RootComponent(
        componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
        themeRepository = themeRepository,
        registryRepository = registryRepository,
        chartDataRepository = chartDataRepository
    )

    return ComposeUIViewController {
        App(rootComponent)
    }
}
