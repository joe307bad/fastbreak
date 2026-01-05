package com.joebad.fastbreak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.joebad.fastbreak.App
import com.joebad.fastbreak.data.api.RegistryApi
import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.data.repository.RegistryRepository
import com.joebad.fastbreak.data.repository.TeamRosterRepository
import com.joebad.fastbreak.domain.registry.ChartDataSynchronizer
import com.joebad.fastbreak.domain.registry.RegistryManager
import com.joebad.fastbreak.domain.teams.TeamRosterSynchronizer
import com.joebad.fastbreak.navigation.RootComponent
import com.joebad.fastbreak.ui.container.PinnedTeamsContainer
import com.joebad.fastbreak.ui.container.RegistryContainer
import com.joebad.fastbreak.ui.theme.SystemThemeDetector
import com.joebad.fastbreak.ui.theme.ThemeRepository
import com.joebad.fastbreak.platform.AppVersion
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display and allow window insets controller
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize app version info
        AppVersion.init(this)

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

        // Create TeamRosterRepository and TeamRosterSynchronizer
        val teamRosterRepository = TeamRosterRepository(settings)
        val teamRosterSynchronizer = TeamRosterSynchronizer(
            teamRosterRepository = teamRosterRepository
        )

        // Create PinnedTeamsContainer
        val pinnedTeamsContainer = PinnedTeamsContainer(
            teamRosterRepository = teamRosterRepository,
            teamRosterSynchronizer = teamRosterSynchronizer,
            scope = scope
        )

        // Create RegistryContainer (Phase 6 - Orbit MVI)
        val registryContainer = RegistryContainer(
            registryManager = registryManager,
            chartDataSynchronizer = chartDataSynchronizer,
            chartDataRepository = chartDataRepository,
            scope = scope,
            pinnedTeamsContainer = pinnedTeamsContainer
        )

        val rootComponent = RootComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            themeRepository = themeRepository,
            registryContainer = registryContainer,
            pinnedTeamsContainer = pinnedTeamsContainer,
            chartDataRepository = chartDataRepository
        )

        setContent {
            App(rootComponent)
        }
    }
}
