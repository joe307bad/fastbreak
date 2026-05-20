package com.joebad.fastbreak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.arkivanov.decompose.defaultComponentContext
import com.joebad.fastbreak.App
import com.joebad.fastbreak.data.api.RegistryApi
import com.joebad.fastbreak.data.repository.RegistryRepository
import com.joebad.fastbreak.data.repository.SqlDelightChartCache
import com.joebad.fastbreak.data.repository.TeamRosterRepository
import com.joebad.fastbreak.data.repository.TopicsRepository
import com.joebad.fastbreak.db.DatabaseDriverFactory
import com.joebad.fastbreak.db.FastbreakDatabase
import com.joebad.fastbreak.domain.registry.ChartSyncManager
import com.joebad.fastbreak.domain.registry.RegistryManager
import com.joebad.fastbreak.domain.teams.TeamRosterSynchronizer
import com.joebad.fastbreak.navigation.RootComponent
import com.joebad.fastbreak.ui.container.PinnedTeamsContainer
import com.joebad.fastbreak.ui.container.RegistryContainer
import com.joebad.fastbreak.ui.theme.SystemThemeDetector
import com.joebad.fastbreak.ui.theme.ThemeRepository
import com.joebad.fastbreak.platform.AppVersion
import com.joebad.fastbreak.platform.initializeImageExporter
import com.joebad.fastbreak.platform.initializeUrlLauncher
import com.joebad.fastbreak.telemetry.TelemetryService
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineExceptionHandler
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

        // Initialize image exporter for sharing charts
        initializeImageExporter(this)

        // Initialize URL launcher for opening external links
        initializeUrlLauncher(this)

        // Initialize telemetry service and track app boot
        TelemetryService.initialize()
        TelemetryService.trackAppBoot()

        val settings = Settings()
        val themeRepository = ThemeRepository(
            settings = settings,
            systemThemeDetector = SystemThemeDetector()
        )

        // Create coroutine scope with exception handler to prevent unhandled exceptions from crashing the app
        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            println("❌ Unhandled coroutine exception: ${exception.message}")
            exception.printStackTrace()
        }
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)

        // Create SQLDelight database and chart cache
        val databaseDriverFactory = DatabaseDriverFactory(this)
        val database = FastbreakDatabase(databaseDriverFactory.createDriver())
        val chartCache = SqlDelightChartCache(database)

        // Create repositories
        val registryRepository = RegistryRepository(settings)

        // Create API services
        val registryApi = RegistryApi()

        // Create RegistryManager
        val registryManager = RegistryManager(
            registryApi = registryApi,
            registryRepository = registryRepository
        )

        // Create TopicsRepository
        val topicsRepository = TopicsRepository(settings)

        // Create ChartSyncManager (replaces ChartDataSynchronizer)
        val chartSyncManager = ChartSyncManager(
            chartCache = chartCache,
            topicsRepository = topicsRepository
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

        // Create RegistryContainer (Orbit MVI)
        val registryContainer = RegistryContainer(
            registryManager = registryManager,
            chartSyncManager = chartSyncManager,
            chartCache = chartCache,
            scope = scope,
            pinnedTeamsContainer = pinnedTeamsContainer
        )

        val rootComponent = RootComponent(
            componentContext = defaultComponentContext(),
            themeRepository = themeRepository,
            registryContainer = registryContainer,
            pinnedTeamsContainer = pinnedTeamsContainer,
            chartCache = chartCache
        )

        setContent {
            App(rootComponent)
        }
    }
}
