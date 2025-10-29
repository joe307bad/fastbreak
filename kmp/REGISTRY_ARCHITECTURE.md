# Registry-Based Chart Data Architecture

## Overview

The Fastbreak app uses a registry-based system to manage sports data visualizations. A mock JSON registry instructs the client on which charts to display for each sport (NFL, MLB, NHL, NBA) and when to update their data. This system integrates with Orbit MVI for state management and uses multiplatform-settings for cross-platform persistence.

## Current State of Implementation

### ✅ Already Implemented

**Data Models** (`data/model/`)
- ✅ `Sport` enum (NFL, MLB, NHL, NBA with displayName)
- ✅ `VisualizationType` sealed interface with implementations:
  - `BarGraphVisualization` - List of `BarGraphDataPoint(label, value)`
  - `ScatterPlotVisualization` - List of `ScatterPlotDataPoint(label, x, y, sum)`
  - `LineChartVisualization` - List of `LineChartSeries` with `LineChartDataPoint(x, y)`
  - `TableVisualization` - List of `TableDataPoint` with columns
- ✅ All models are `@Serializable`
- ✅ `kotlinx-datetime.Instant` for timestamps

**Mock API** (`data/api/`)
- ✅ `MockedDataApi` - Generates fake sports analytics data
- ✅ Simulates network delay (800ms)
- ✅ Sport-specific data generation for scatter plots, bar graphs, and line charts

**Persistence** (`data/repository/`)
- ✅ `ThemeRepository` using multiplatform-settings
- ✅ Key-value storage pattern (SharedPreferences on Android, NSUserDefaults on iOS)

**UI Components** (`ui/`)
- ✅ HomeScreen with sport tabs and visualization selection
- ✅ DataVizScreen with loading/error/success states
- ✅ Visualization components: `FourQuadrantScatterPlot`, `BarChartComponent`, `LineChartComponent`
- ✅ All charts support pan & zoom gestures
- ✅ `DataTableComponent` for tabular data display
- ✅ Theme system with light/dark mode

**Navigation**
- ✅ Decompose-based navigation with `RootComponent`, `HomeComponent`, `DataVizComponent`
- ✅ Stack-based navigation with serializable configs

**Dependencies**
- ✅ Orbit Core and Orbit Compose included (but not yet actively used)
- ✅ kotlinx-serialization
- ✅ kotlinx-coroutines
- ✅ kotlinx-datetime
- ✅ multiplatform-settings
- ✅ Compose Multiplatform
- ✅ Decompose navigation

### 🚧 To Be Implemented

- Registry data models (ChartDefinition, Registry, RegistryMetadata)
- RegistryRepository with multiplatform-settings
- ChartDataRepository with multiplatform-settings
- RegistryManager with 12-hour check logic
- ChartDataSynchronizer for timestamp comparison
- **Orbit MVI integration** (replace current Flow-based state management)
- Mock registry JSON generation
- Persistence of registry and chart data between app restarts
- UI components for registry sync status

---

## Registry Architecture

### Registry File Structure (Mock)

The mock registry returns a JSON structure that defines available charts:

```json
{
  "version": "1.0",
  "lastUpdated": "2025-10-28T10:30:00Z",
  "charts": [
    {
      "id": "nfl-efficiency-scatter",
      "sport": "NFL",
      "title": "Team Efficiency Analysis",
      "subtitle": "Offensive vs Defensive Performance",
      "lastUpdated": "2025-10-28T10:30:00Z",
      "visualizationType": "SCATTER_PLOT",
      "mockDataType": "scatter"
    },
    {
      "id": "nba-scoring-differential",
      "sport": "NBA",
      "title": "Team Point Differential",
      "subtitle": "Season performance by team",
      "lastUpdated": "2025-10-28T09:15:00Z",
      "visualizationType": "BAR_GRAPH",
      "mockDataType": "bar"
    },
    {
      "id": "mlb-season-progression",
      "sport": "MLB",
      "title": "Season Win Progression",
      "subtitle": "Team performance over time",
      "lastUpdated": "2025-10-28T08:00:00Z",
      "visualizationType": "LINE_CHART",
      "mockDataType": "line"
    }
  ]
}
```

### Registry Update Flow

```
┌─────────────────┐
│   App Launch    │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────┐
│ RegistryContainer           │
│ (Orbit MVI)                 │
│ - Check Last Registry Time  │
│   from multiplatform-settings│
└────────┬────────────────────┘
         │
         ▼
    ┌──────────┐
    │ > 12hrs?  │
    └─┬────┬───┘
  Yes │    │ No
      ▼    │
┌─────────────────┐ │
│ Intent:         │ │
│ LoadRegistry    │ │
└─────────────────┘ │
      │             │
      ▼             ▼
┌─────────────────────────────┐
│ SideEffect:                 │
│ FetchMockRegistry           │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│ Compare lastUpdated         │
│ timestamps with cached data │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│ For charts needing update:  │
│ Intent: LoadChartData(id)   │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│ SideEffect:                 │
│ FetchMockChartData          │
│ → Reduce to state           │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│ Persist registry & chart    │
│ data to multiplatform-      │
│ settings as JSON strings    │
└─────────────────────────────┘
```

---

## Data Models

### New Models to Create

**ChartDefinition.kt**
```kotlin
@Serializable
data class ChartDefinition(
    val id: String,
    val sport: Sport, // Existing enum
    val title: String,
    val subtitle: String,
    val lastUpdated: Instant,
    val visualizationType: VizType, // Maps to existing VisualizationType
    val mockDataType: String // "scatter", "bar", "line" - tells MockedDataApi what to generate
)

enum class VizType {
    SCATTER_PLOT, BAR_GRAPH, LINE_CHART;

    fun toVisualizationType(): VisualizationType {
        // Will map to existing sealed interface implementations
    }
}
```

**Registry.kt**
```kotlin
@Serializable
data class Registry(
    val version: String,
    val lastUpdated: Instant,
    val charts: List<ChartDefinition>
)
```

**RegistryMetadata.kt**
```kotlin
@Serializable
data class RegistryMetadata(
    val lastDownloadTime: Instant,
    val registryVersion: String
)
```

**CachedChartData.kt**
```kotlin
@Serializable
data class CachedChartData(
    val chartId: String,
    val lastUpdated: Instant,
    val visualizationType: VizType,
    val cachedAt: Instant,
    val dataJson: String // Serialized VisualizationType as JSON
)
```

---

## Orbit MVI Integration

### State Container Pattern

Replace current `DataVizViewModel` with Orbit Container pattern:

**RegistryState.kt**
```kotlin
data class RegistryState(
    val registry: Registry? = null,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncTime: Instant? = null,
    val syncProgress: SyncProgress? = null,
    val error: String? = null,
    val diagnostics: DiagnosticsInfo = DiagnosticsInfo()
)

data class SyncProgress(
    val current: Int,
    val total: Int,
    val currentChart: String
)

data class DiagnosticsInfo(
    val lastRegistryFetch: Instant? = null,
    val lastCacheUpdate: Instant? = null,
    val cachedChartsCount: Int = 0,
    val registryVersion: String? = null,
    val totalCacheSize: Long = 0, // in bytes (estimated)
    val failedSyncs: Int = 0,
    val lastError: String? = null,
    val isStale: Boolean = false, // true if > 12 hours since last fetch
    val isSyncing: Boolean = false // true while actively syncing
)
```

**RegistrySideEffect.kt**
```kotlin
sealed interface RegistrySideEffect {
    data class ShowError(val message: String) : RegistrySideEffect
    data object SyncCompleted : RegistrySideEffect
    data class NavigateToChart(val chartId: String) : RegistrySideEffect
}
```

**RegistryContainer.kt** (Orbit Container)
```kotlin
class RegistryContainer(
    private val registryManager: RegistryManager,
    private val chartDataSync: ChartDataSynchronizer
) : ContainerHost<RegistryState, RegistrySideEffect> {

    override val container = scope.container<RegistryState, RegistrySideEffect>(
        initialState = RegistryState()
    )

    fun loadRegistry() = intent {
        reduce { state.copy(isLoading = true, error = null) }

        registryManager.checkAndUpdateRegistry()
            .onSuccess { registry ->
                // Update diagnostics
                val diagnostics = loadDiagnostics()
                reduce {
                    state.copy(
                        registry = registry,
                        isLoading = false,
                        lastSyncTime = Clock.System.now(),
                        diagnostics = diagnostics
                    )
                }
            }
            .onFailure { error ->
                // Increment failed syncs counter
                val diagnostics = state.diagnostics.copy(
                    failedSyncs = state.diagnostics.failedSyncs + 1,
                    lastError = error.message
                )
                reduce {
                    state.copy(
                        isLoading = false,
                        error = error.message,
                        diagnostics = diagnostics
                    )
                }
                postSideEffect(RegistrySideEffect.ShowError(error.message ?: "Unknown error"))
            }
    }

    private suspend fun loadDiagnostics(): DiagnosticsInfo {
        val metadata = registryManager.getMetadata()
        val chartIds = chartDataSync.getCachedChartIds()
        val now = Clock.System.now()

        return DiagnosticsInfo(
            lastRegistryFetch = metadata?.lastDownloadTime,
            lastCacheUpdate = chartIds.maxOfOrNull { id ->
                chartDataSync.getChartCacheTime(id)
            },
            cachedChartsCount = chartIds.size,
            registryVersion = metadata?.registryVersion,
            totalCacheSize = chartDataSync.estimateCacheSize(),
            failedSyncs = state.diagnostics.failedSyncs,
            lastError = state.diagnostics.lastError,
            isStale = metadata?.let { now - it.lastDownloadTime > 12.hours } ?: true
        )
    }

    fun syncCharts() = intent {
        reduce { state.copy(isSyncing = true) }

        val registry = state.registry ?: return@intent

        chartDataSync.synchronizeCharts(registry)
            .collect { progress ->
                reduce { state.copy(syncProgress = progress) }
            }

        reduce {
            state.copy(isSyncing = false, syncProgress = null)
        }
        postSideEffect(RegistrySideEffect.SyncCompleted)
    }

    fun forceRefresh() = intent {
        registryManager.forceRefreshRegistry()
        loadRegistry()
    }
}
```

**ChartDataState.kt** (Individual chart screen)
```kotlin
data class ChartDataState(
    val chartDefinition: ChartDefinition? = null,
    val visualizationData: VisualizationType? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface ChartDataSideEffect {
    data class ShowError(val message: String) : ChartDataSideEffect
}
```

**ChartDataContainer.kt**
```kotlin
class ChartDataContainer(
    private val chartId: String,
    private val chartDataRepo: ChartDataRepository,
    private val mockedApi: MockedDataApi
) : ContainerHost<ChartDataState, ChartDataSideEffect> {

    override val container = scope.container<ChartDataState, ChartDataSideEffect>(
        initialState = ChartDataState()
    )

    fun loadChartData() = intent {
        reduce { state.copy(isLoading = true, error = null) }

        // First try to load from cache
        chartDataRepo.getChartData(chartId)?.let { cached ->
            val vizData = Json.decodeFromString<VisualizationType>(cached.dataJson)
            reduce {
                state.copy(
                    visualizationData = vizData,
                    isLoading = false
                )
            }
            return@intent
        }

        // If not cached, fetch from mock API
        // ... fetch logic
    }
}
```

---

## UI Components

### Compact Diagnostics UI (Phase 2 - Early Integration)

Add a simple, scrollable diagnostics section to `DrawerMenu.kt`:

**DiagnosticsFormatters.kt**
```kotlin
// Helper functions for formatting
fun formatTimeAgo(instant: Instant): String {
    val now = Clock.System.now()
    val duration = now - instant

    return when {
        duration < 1.minutes -> "now"
        duration < 1.hours -> "${duration.inWholeMinutes}m"
        duration < 24.hours -> "${duration.inWholeHours}h"
        duration < 7.days -> "${duration.inWholeDays}d"
        else -> "${duration.inWholeDays / 7}w"
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${bytes / (1024 * 1024)}MB"
    }
}
```

**SyncStatusRow.kt** (Compact one-line status)
```kotlin
@Composable
fun SyncStatusRow(
    diagnostics: DiagnosticsInfo,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when {
                        diagnostics.lastError != null -> Color.Red
                        diagnostics.isStale -> Color(0xFFFFAA00) // Amber
                        else -> Color.Green
                    },
                    shape = CircleShape
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Status text
        Text(
            text = when {
                diagnostics.lastError != null -> "Error"
                diagnostics.isStale -> "Stale"
                diagnostics.isSyncing -> "Syncing..."
                else -> "Up to date"
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Cache info
        Text(
            text = "${diagnostics.cachedChartsCount} charts • ${formatBytes(diagnostics.totalCacheSize)}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}
```

**RegistryOverviewList.kt** (Scrollable chart list)
```kotlin
@Composable
fun RegistryOverviewList(
    registry: Registry?,
    modifier: Modifier = Modifier
) {
    if (registry == null) {
        Text(
            text = "No registry loaded",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier) {
        // Group charts by sport
        Sport.entries.forEach { sport ->
            val sportCharts = registry.charts.filter { it.sport == sport }
            if (sportCharts.isNotEmpty()) {
                // Sport header
                Text(
                    text = sport.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                // Chart items
                sportCharts.forEach { chart ->
                    ChartOverviewItem(chart)
                }
            }
        }
    }
}

@Composable
private fun ChartOverviewItem(chart: ChartDefinition) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = chart.title,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTimeAgo(chart.lastUpdated),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1
        )
    }
}
```

**Updated DrawerMenu.kt** (Scrollable with diagnostics)
```kotlin
@Composable
fun DrawerMenu(
    onThemeChange: (ThemeMode) -> Unit,
    currentTheme: ThemeMode,
    registry: Registry?, // New parameter
    diagnostics: DiagnosticsInfo, // New parameter
    onRefreshRegistry: () -> Unit, // New parameter
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // Make scrollable
            .padding(16.dp)
    ) {
        // Existing theme section
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Theme toggle (existing code)
        // ... your existing Cupertino segmented control ...

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Sync status (compact)
        Text(
            text = "Sync Status",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        SyncStatusRow(diagnostics = diagnostics)

        Spacer(modifier = Modifier.height(12.dp))

        // Refresh button
        OutlinedButton(
            onClick = onRefreshRegistry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Registry", fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Registry overview
        Text(
            text = "Registry Overview",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        RegistryOverviewList(registry = registry)

        // Add some bottom padding for scroll
        Spacer(modifier = Modifier.height(16.dp))
    }
}
```

### Full Diagnostics Panel (Phase 7 - Advanced Features)

For Phase 7, add more detailed diagnostics with error handling:

**Additional fields to show:**
- Last error message (if present, max 2 lines with ellipsis)
- Failed syncs counter
- Registry version
- Last fetch time (full timestamp on long press?)
- Clear cache button (destructive action)

---

## Component Architecture

### 1. MockRegistryApi
**Responsibility:** Generate mock registry JSON

**Location:** `data/api/MockRegistryApi.kt`

```kotlin
class MockRegistryApi {
    suspend fun fetchRegistry(): Result<Registry> = runCatching {
        delay(500) // Simulate network

        Registry(
            version = "1.0",
            lastUpdated = Clock.System.now(),
            charts = listOf(
                ChartDefinition(
                    id = "nfl-efficiency",
                    sport = Sport.NFL,
                    title = "Team Efficiency",
                    subtitle = "Offensive vs Defensive",
                    lastUpdated = Clock.System.now().minus(2.hours),
                    visualizationType = VizType.SCATTER_PLOT,
                    mockDataType = "scatter"
                ),
                // ... more charts for each sport
            )
        )
    }
}
```

### 2. RegistryRepository
**Responsibility:** Persist registry using multiplatform-settings

**Location:** `data/repository/RegistryRepository.kt`

```kotlin
class RegistryRepository(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveRegistry(registry: Registry) {
        val jsonString = json.encodeToString(Registry.serializer(), registry)
        settings.putString(KEY_REGISTRY, jsonString)
    }

    suspend fun getRegistry(): Registry? {
        return settings.getStringOrNull(KEY_REGISTRY)?.let {
            json.decodeFromString(Registry.serializer(), it)
        }
    }

    suspend fun saveMetadata(metadata: RegistryMetadata) {
        val jsonString = json.encodeToString(RegistryMetadata.serializer(), metadata)
        settings.putString(KEY_METADATA, jsonString)
    }

    suspend fun getMetadata(): RegistryMetadata? {
        return settings.getStringOrNull(KEY_METADATA)?.let {
            json.decodeFromString(RegistryMetadata.serializer(), it)
        }
    }

    companion object {
        private const val KEY_REGISTRY = "registry_data"
        private const val KEY_METADATA = "registry_metadata"
    }
}
```

### 3. ChartDataRepository
**Responsibility:** Persist chart data using multiplatform-settings

**Location:** `data/repository/ChartDataRepository.kt`

```kotlin
class ChartDataRepository(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveChartData(chartId: String, data: CachedChartData) {
        val jsonString = json.encodeToString(CachedChartData.serializer(), data)
        settings.putString("chart_$chartId", jsonString)
    }

    suspend fun getChartData(chartId: String): CachedChartData? {
        return settings.getStringOrNull("chart_$chartId")?.let {
            json.decodeFromString(CachedChartData.serializer(), it)
        }
    }

    suspend fun getAllChartIds(): List<String> {
        // multiplatform-settings doesn't have a keys() method
        // So we'll store a list of chart IDs separately
        return settings.getStringOrNull(KEY_CHART_IDS)?.let {
            json.decodeFromString<List<String>>(it)
        } ?: emptyList()
    }

    suspend fun saveChartIds(ids: List<String>) {
        val jsonString = json.encodeToString(ids)
        settings.putString(KEY_CHART_IDS, jsonString)
    }

    companion object {
        private const val KEY_CHART_IDS = "all_chart_ids"
    }
}
```

### 4. RegistryManager
**Responsibility:** Orchestrates registry updates and 12-hour checks

**Location:** `domain/registry/RegistryManager.kt`

```kotlin
class RegistryManager(
    private val mockRegistryApi: MockRegistryApi,
    private val registryRepo: RegistryRepository
) {
    suspend fun checkAndUpdateRegistry(): Result<Registry> = runCatching {
        val metadata = registryRepo.getMetadata()
        val now = Clock.System.now()

        val shouldDownload = metadata == null ||
            now - metadata.lastDownloadTime > 12.hours

        if (shouldDownload) {
            // Download new registry
            val registry = mockRegistryApi.fetchRegistry().getOrThrow()

            // Save registry and metadata
            registryRepo.saveRegistry(registry)
            registryRepo.saveMetadata(
                RegistryMetadata(
                    lastDownloadTime = now,
                    registryVersion = registry.version
                )
            )

            registry
        } else {
            // Return cached registry
            registryRepo.getRegistry() ?: throw Exception("No cached registry found")
        }
    }

    suspend fun forceRefreshRegistry(): Result<Registry> = runCatching {
        val registry = mockRegistryApi.fetchRegistry().getOrThrow()
        registryRepo.saveRegistry(registry)
        registryRepo.saveMetadata(
            RegistryMetadata(
                lastDownloadTime = Clock.System.now(),
                registryVersion = registry.version
            )
        )
        registry
    }
}
```

### 5. ChartDataSynchronizer
**Responsibility:** Compare timestamps and download updated charts

**Location:** `domain/registry/ChartDataSynchronizer.kt`

```kotlin
class ChartDataSynchronizer(
    private val chartDataRepo: ChartDataRepository,
    private val mockedDataApi: MockedDataApi
) {
    suspend fun synchronizeCharts(
        registry: Registry
    ): Flow<SyncProgress> = flow {
        val chartsNeedingUpdate = registry.charts.filter { chart ->
            needsUpdate(chart)
        }

        chartsNeedingUpdate.forEachIndexed { index, chartDef ->
            emit(SyncProgress(
                current = index + 1,
                total = chartsNeedingUpdate.size,
                currentChart = chartDef.title
            ))

            downloadAndCacheChart(chartDef)
        }
    }

    private suspend fun needsUpdate(chartDef: ChartDefinition): Boolean {
        val cached = chartDataRepo.getChartData(chartDef.id) ?: return true
        return chartDef.lastUpdated > cached.lastUpdated
    }

    private suspend fun downloadAndCacheChart(chartDef: ChartDefinition) {
        // Use MockedDataApi to generate data based on chart definition
        val vizData = mockedDataApi.fetchVisualizationData(
            sport = chartDef.sport,
            vizType = chartDef.visualizationType
        )

        // Serialize visualization data
        val dataJson = when (vizData) {
            is BarGraphVisualization -> Json.encodeToString(vizData)
            is ScatterPlotVisualization -> Json.encodeToString(vizData)
            is LineChartVisualization -> Json.encodeToString(vizData)
            else -> throw IllegalArgumentException("Unsupported viz type")
        }

        // Cache the data
        val cachedData = CachedChartData(
            chartId = chartDef.id,
            lastUpdated = chartDef.lastUpdated,
            visualizationType = chartDef.visualizationType,
            cachedAt = Clock.System.now(),
            dataJson = dataJson
        )

        chartDataRepo.saveChartData(chartDef.id, cachedData)
    }
}
```

---

## Implementation Phases

### Phase 1: Foundation & Data Models ✅ (Partially Done)
**Goal:** Set up basic data structures

**Already Done:**
- ✅ Sport enum exists
- ✅ VisualizationType sealed interface exists
- ✅ kotlinx-serialization configured
- ✅ kotlinx-datetime included

**Remaining Tasks:**
- [ ] Create `ChartDefinition` data class
- [ ] Create `Registry` data class
- [ ] Create `RegistryMetadata` data class
- [ ] Create `CachedChartData` data class
- [ ] Create `VizType` enum with mapping to existing `VisualizationType`

**Deliverable:** New registry-related models that work with existing models

---

### Phase 2: Mock Registry API + Early UI Integration
**Goal:** Create mock registry data source and add basic diagnostics UI

**Tasks:**
- [ ] Create `MockRegistryApi` class
- [ ] Generate mock registry with 3-4 charts per sport (12-16 total)
- [ ] Add simulation delay (500-800ms)
- [ ] Create factory methods for different chart definitions
- [ ] **Add basic diagnostics UI to DrawerMenu:**
  - Make drawer content scrollable with `Column + Modifier.verticalScroll()`
  - Add compact sync status section (single line with indicator)
  - Show cache count and size (single line: "12 charts • 2.5 MB")
  - Add scrollable registry overview:
    - Each chart as single line: "NFL: Team Efficiency • 2h ago"
    - Use ellipsis for long titles: `maxLines = 1, overflow = TextOverflow.Ellipsis`
    - Group by sport with small sport headers
    - Show last updated time in relative format ("2h ago")
  - Add simple "Refresh Registry" button
  - Keep existing theme toggle at top
- [ ] Create compact `SyncStatusRow` composable (one-line status)
- [ ] Create `RegistryOverviewList` composable (scrollable chart list)
- [ ] Create helper `formatTimeAgo()` function

**Deliverable:** Working mock registry API with basic diagnostics UI for early testing

---

### Phase 3: Persistence Layer with multiplatform-settings
**Goal:** Persist registry and chart data

**Already Done:**
- ✅ multiplatform-settings dependency
- ✅ `ThemeRepository` as reference implementation

**Tasks:**
- [ ] Create `RegistryRepository` using Settings
- [ ] Create `ChartDataRepository` using Settings
- [ ] Implement JSON serialization/deserialization for storage
- [ ] Add methods for saving/retrieving registry metadata
- [ ] Handle chart ID list management

**Deliverable:** Working persistence with data surviving app restarts

---

### Phase 4: Registry Management Logic
**Goal:** Implement 12-hour check and registry updates

**Tasks:**
- [ ] Create `RegistryManager` class
- [ ] Implement `checkAndUpdateRegistry()` with 12-hour logic
- [ ] Implement `forceRefreshRegistry()`
- [ ] **Add comprehensive error handling:**
  - Catch and wrap exceptions in Result type
  - Fall back to cached registry on network errors
  - Retry logic with exponential backoff (optional for mock)
  - Validation of registry JSON structure
  - Handle corrupted cache gracefully
- [ ] Add `getMetadata()` helper method for diagnostics
- [ ] Test with different time scenarios (< 12h, > 12h, first launch)
- [ ] Test error scenarios (no cache, corrupted cache, network failure)

**Deliverable:** Working registry download and caching with robust error handling

---

### Phase 5: Chart Data Synchronization
**Goal:** Compare timestamps and update charts

**Already Done:**
- ✅ `MockedDataApi` generates chart data

**Tasks:**
- [ ] Create `ChartDataSynchronizer` class
- [ ] Implement timestamp comparison logic (`needsUpdate()`)
- [ ] Integrate with `MockedDataApi` for data generation
- [ ] Add progress tracking via Flow (emit SyncProgress)
- [ ] **Handle partial sync failures gracefully:**
  - Continue syncing other charts if one fails
  - Collect all errors and report at the end
  - Don't fail entire sync for individual chart failures
  - Track failed sync count in diagnostics
- [ ] Add helper methods for diagnostics:
  - `getCachedChartIds()` - Return list of all cached chart IDs
  - `getChartCacheTime(chartId)` - Get when a chart was cached
  - `estimateCacheSize()` - Calculate total cache size in bytes
  - `clearAllCache()` - Delete all cached chart data
- [ ] Implement `downloadAndCacheChart()` with error handling
- [ ] Test concurrent chart downloads (respect MAX_CONCURRENT_DOWNLOADS)
- [ ] Test partial failure scenarios

**Deliverable:** Working chart synchronization with progress and robust error handling

---

### Phase 6: Orbit MVI Integration
**Goal:** Replace current state management with Orbit

**Already Done:**
- ✅ Orbit dependencies included

**Tasks:**
- [ ] Create `RegistryState` and `RegistrySideEffect`
- [ ] Create `RegistryContainer` with Orbit Container pattern
- [ ] Create `ChartDataState` and `ChartDataSideEffect`
- [ ] Create `ChartDataContainer`
- [ ] Replace `DataVizViewModel` with `ChartDataContainer`
- [ ] Update HomeScreen to use `RegistryContainer`
- [ ] Update DataVizScreen to use `ChartDataContainer`

**Deliverable:** Full Orbit MVI integration

---

### Phase 7: UI Integration & Diagnostics
**Goal:** Connect registry to UI and add diagnostics panel

**Already Done:**
- ✅ HomeScreen with sport tabs
- ✅ DataVizScreen with loading states
- ✅ Visualization components
- ✅ DrawerMenu with theme toggle

**Tasks:**
- [ ] Update HomeScreen to display registry charts instead of hardcoded list
- [ ] Integrate RegistryContainer with RootComponent
- [ ] Pass diagnostics state to DrawerMenu
- [ ] **Create DiagnosticsPanel composable**
  - Show registry version
  - Display last registry fetch time (formatted as "2h ago", "1d ago", etc.)
  - Display last cache update time
  - Show cached charts count
  - Display total cache size in KB/MB
  - Show failed syncs counter with warning color
  - Display last error message if present
  - Add status indicator (Green=Healthy, Yellow=Stale, Red=Error)
- [ ] **Add diagnostic action buttons**
  - Manual "Refresh" button to force registry update
  - "Clear Cache" button to reset all cached data
- [ ] Add helper functions:
  - `formatTimeAgo(Instant)` - Format timestamps as relative time
  - `formatBytes(Long)` - Format byte sizes as human-readable
- [ ] Add pull-to-refresh on HomeScreen
- [ ] Show sync progress indicator during chart updates
- [ ] Handle offline mode gracefully with cached data
- [ ] Add error snackbar/toast for sync failures
- [ ] Update RootComponent to provide diagnostics to drawer
- [ ] Test drawer menu with diagnostics on both iOS and Android

**Deliverable:** Full UI integration with comprehensive diagnostics panel

---

### Phase 8: Error Handling, Polish & Testing
**Goal:** Production readiness with comprehensive error handling

**Tasks:**
- [ ] **Comprehensive error handling verification:**
  - Verify all Result types are properly handled
  - Test network timeout scenarios
  - Test JSON parsing errors
  - Test cache corruption scenarios
  - Test first app launch (no cache)
  - Verify graceful degradation (show old data on errors)
  - Test simultaneous sync attempts
  - Verify error messages are user-friendly
- [ ] **Testing scenarios:**
  - Test app restart with cached data (data should persist)
  - Test app restart after 12+ hours (should trigger update)
  - Test 12-hour boundary conditions (exactly 12h, 11h59m, 12h01m)
  - Test partial sync failures (some charts fail, others succeed)
  - Test offline scenarios (airplane mode)
  - Test cache clear functionality
  - Test force refresh functionality
  - Test diagnostics panel on both iOS and Android
  - Test with empty registry
  - Test with large registry (50+ charts)
- [ ] **Polish & UX:**
  - Add logging for sync operations (console logs for debugging)
  - Add loading animations during sync
  - Add success/error Snackbar/Toast messages
  - Ensure proper loading states in UI
  - Add skeleton loaders for chart list
  - Optimize JSON serialization performance
  - Add haptic feedback on refresh (iOS/Android)
  - Verify monospace font consistency in diagnostics
- [ ] **Performance:**
  - Profile memory usage during sync
  - Optimize concurrent downloads
  - Test with slow network simulation
  - Verify no memory leaks
- [ ] **Documentation:**
  - Add inline code comments
  - Document error handling patterns
  - Create developer troubleshooting guide

**Deliverable:** Production-ready registry system with robust error handling and excellent UX

---

## Technical Considerations

### Persistence with multiplatform-settings
- Store complex objects as JSON strings
- Use kotlinx-serialization for encode/decode
- Keys pattern: `registry_data`, `registry_metadata`, `chart_{chartId}`
- No query capabilities - keep chart IDs in a separate list
- Atomic updates - save all related data together

### Orbit MVI Pattern
```kotlin
// Intent (user action)
fun loadRegistry() = intent { ... }

// Reduce (update state)
reduce { state.copy(isLoading = true) }

// Side Effect (one-time events)
postSideEffect(RegistrySideEffect.ShowError("..."))
```

### Time Calculations
```kotlin
val shouldUpdate = Clock.System.now() - lastDownloadTime > 12.hours
```

### Mock Data Integration
```kotlin
// MockedDataApi already supports sport + viz type
val data = MockedDataApi(sport).fetchVisualizationData(vizType)
```

### Error Handling Strategy

**Result Type Pattern:**
```kotlin
// Always wrap operations in Result
suspend fun checkAndUpdateRegistry(): Result<Registry> = runCatching {
    // ... operation
}

// Handle in caller
registryManager.checkAndUpdateRegistry()
    .onSuccess { registry -> /* update state */ }
    .onFailure { error -> /* show error, use cache */ }
```

**Graceful Degradation:**
- **Network failures:** Use cached data, show "Using cached data" indicator
- **Partial sync failures:** Continue syncing other charts, track failures in diagnostics
- **Corrupted cache:** Delete corrupted entry, re-download on next sync
- **First launch (no cache):** Show loading, fail gracefully with clear message
- **Registry format changes:** Version checking, migrate old formats
- **Stale data (> 12 hours):** Show warning indicator in diagnostics, allow usage

**Error Categories:**
1. **Recoverable Errors** (use cache, retry later)
   - Network timeout
   - Server unavailable (mock API delay)
   - Temporary parsing errors

2. **User-Actionable Errors** (show message with action)
   - No internet connection → "Check your connection"
   - Cache full → "Clear cache to continue"
   - First launch failure → "Retry" button in diagnostics panel

3. **Fatal Errors** (log, report, but don't crash)
   - Corrupted settings storage
   - Invalid registry version
   - Out of disk space

**Error Messages (User-Friendly):**
```kotlin
sealed class RegistryError(val userMessage: String, val technicalDetails: String) {
    data object NetworkTimeout : RegistryError(
        "Connection timed out. Using cached data.",
        "Registry fetch exceeded 30s timeout"
    )
    data object NoCache : RegistryError(
        "Unable to load data. Please check your connection.",
        "No cached registry available"
    )
    data object CorruptedCache : RegistryError(
        "Data corrupted. Clearing cache...",
        "JSON deserialization failed"
    )
    data class PartialSync(val failed: Int, val total: Int) : RegistryError(
        "Updated ${total - failed} of $total charts",
        "Failed chart IDs: [...]"
    )
}
```

**Diagnostics Integration:**
- Track failed sync count in `DiagnosticsInfo.failedSyncs`
- Store last error message in `DiagnosticsInfo.lastError` (user-friendly)
- Show visual indicator: Green (healthy), Yellow (stale), Red (error)
- Provide manual "Refresh" button to retry after errors
- "Clear Cache" button to recover from corruption
- Log errors but never crash the app

### Testing with Mock APIs
- No network dependencies
- Fast synchronous tests
- Predictable data generation
- Easy to test edge cases (empty registry, corrupted data, etc.)

---

## Project Structure

```
shared/src/commonMain/kotlin/com/joebad/fastbreak/
├── data/
│   ├── api/
│   │   ├── MockedDataApi.kt              ✅ Existing
│   │   └── MockRegistryApi.kt            🚧 New
│   ├── model/
│   │   ├── Sport.kt                      ✅ Existing
│   │   ├── VisualizationTypes.kt         ✅ Existing
│   │   ├── ChartDefinition.kt            🚧 New
│   │   ├── Registry.kt                   🚧 New
│   │   ├── RegistryMetadata.kt           🚧 New
│   │   └── CachedChartData.kt            🚧 New
│   └── repository/
│       ├── ThemeRepository.kt            ✅ Existing
│       ├── RegistryRepository.kt         🚧 New
│       └── ChartDataRepository.kt        🚧 New
├── domain/
│   └── registry/
│       ├── RegistryManager.kt            🚧 New
│       └── ChartDataSynchronizer.kt      🚧 New
├── ui/
│   ├── HomeScreen.kt                     ✅ Existing (needs update)
│   ├── DataVizScreen.kt                  ✅ Existing (needs update)
│   ├── DrawerMenu.kt                     ✅ Existing (needs update)
│   ├── diagnostics/
│   │   ├── DiagnosticsPanel.kt           🚧 New
│   │   └── DiagnosticsFormatters.kt      🚧 New (formatTimeAgo, formatBytes)
│   ├── container/
│   │   ├── RegistryContainer.kt          🚧 New
│   │   ├── RegistryState.kt              🚧 New
│   │   ├── ChartDataContainer.kt         🚧 New
│   │   └── ChartDataState.kt             🚧 New
│   └── visualizations/                   ✅ Existing
└── navigation/                            ✅ Existing (Decompose)
```

---

## Configuration

```kotlin
object RegistryConfig {
    const val UPDATE_INTERVAL_HOURS = 12L
    const val MOCK_NETWORK_DELAY_MS = 800L
    const val MAX_CONCURRENT_DOWNLOADS = 3
    const val CACHE_STALE_THRESHOLD_HOURS = 24L
}
```

---

## Next Steps

1. **Start with Phase 1**: Create the new data models (ChartDefinition, Registry, etc.)
2. **Phase 2**: Implement MockRegistryApi
3. **Phase 3**: Create repositories using multiplatform-settings pattern from ThemeRepository
4. **Phase 4**: Build RegistryManager with 12-hour logic
5. **Phase 5**: Implement ChartDataSynchronizer
6. **Phase 6**: Migrate to Orbit MVI pattern
7. **Phase 7**: Update UI to use registry-driven charts
8. **Phase 8**: Polish and test thoroughly

Ready to start implementation?
