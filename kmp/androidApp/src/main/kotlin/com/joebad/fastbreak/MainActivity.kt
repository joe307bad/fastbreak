package com.joebad.fastbreak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.joebad.fastbreak.App
import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.data.repository.RegistryRepository
import com.joebad.fastbreak.navigation.RootComponent
import com.joebad.fastbreak.ui.theme.SystemThemeDetector
import com.joebad.fastbreak.ui.theme.ThemeRepository
import com.russhwolf.settings.Settings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = Settings()
        val themeRepository = ThemeRepository(
            settings = settings,
            systemThemeDetector = SystemThemeDetector()
        )
        val registryRepository = RegistryRepository(settings)
        val chartDataRepository = ChartDataRepository(settings)

        val rootComponent = RootComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            themeRepository = themeRepository,
            registryRepository = registryRepository,
            chartDataRepository = chartDataRepository
        )

        setContent {
            App(rootComponent)
        }
    }
}
