package com.joebad.fastbreak

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.joebad.fastbreak.data.api.RegistryApi
import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.data.repository.RegistryRepository
import com.joebad.fastbreak.domain.registry.ChartDataSynchronizer
import com.joebad.fastbreak.domain.registry.RegistryManager
import com.joebad.fastbreak.navigation.RootComponent
import com.joebad.fastbreak.ui.container.RegistryContainer
import com.joebad.fastbreak.ui.theme.SystemThemeDetector
import com.joebad.fastbreak.ui.theme.ThemeRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val settings = Settings()
    val themeRepository = ThemeRepository(
        settings = settings,
        systemThemeDetector = SystemThemeDetector()
    )

    // Create coroutine scope
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Create repositories
    val registryRepository = RegistryRepository(settings)
    val chartDataRepository = ChartDataRepository(settings)

    // Create API services
    val registryApi = RegistryApi()

    // Create RegistryManager (Phase 4)
    val registryManager = RegistryManager(
        registryApi = registryApi,
        registryRepository = registryRepository
    )

    // Create ChartDataSynchronizer (Phase 5)
    val chartDataSynchronizer = ChartDataSynchronizer(
        chartDataRepository = chartDataRepository
    )

    // Create RegistryContainer (Phase 6 - Orbit MVI)
    val registryContainer = RegistryContainer(
        registryManager = registryManager,
        chartDataSynchronizer = chartDataSynchronizer,
        chartDataRepository = chartDataRepository,
        scope = scope
    )

    val rootComponent = RootComponent(
        componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
        themeRepository = themeRepository,
        registryContainer = registryContainer,
        chartDataRepository = chartDataRepository
    )

    return ComposeUIViewController {
        App(rootComponent)
    }
}
