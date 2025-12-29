package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import com.joebad.fastbreak.ui.diagnostics.RegistryOverviewList
import com.joebad.fastbreak.ui.theme.ThemeMode

@Composable
fun DrawerMenu(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    registry: Registry = Registry.empty(),
    diagnostics: DiagnosticsInfo = DiagnosticsInfo(),
    onRefreshRegistry: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onChartClick: (com.joebad.fastbreak.data.model.ChartDefinition) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Registry overview
                Text(
                    text = "chart registry overview",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                RegistryOverviewList(
                    registry = registry,
                    onChartClick = onChartClick
                )
            }

            // Settings button pinned to bottom
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            FilledTonalButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("settings-button"),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "settings",
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
