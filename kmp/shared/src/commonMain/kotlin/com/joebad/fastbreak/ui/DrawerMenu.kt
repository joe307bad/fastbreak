package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import com.joebad.fastbreak.ui.diagnostics.RegistryOverviewList
import com.joebad.fastbreak.ui.diagnostics.SyncStatusRow
import com.joebad.fastbreak.ui.theme.ThemeMode
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControl
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlTab

@Composable
fun DrawerMenu(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    registry: Registry = Registry.empty(),
    diagnostics: DiagnosticsInfo = DiagnosticsInfo(),
    onRefreshRegistry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "settings",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.outline
            )

            // Theme selector
            Text(
                text = "theme",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            CupertinoSegmentedControl(
                selectedTabIndex = if (currentTheme == ThemeMode.LIGHT) 0 else 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                CupertinoSegmentedControlTab(
                    isSelected = currentTheme == ThemeMode.LIGHT,
                    onClick = { onThemeChange(ThemeMode.LIGHT) }
                ) {
                    Text(
                        text = "light",
                        color = if (currentTheme == ThemeMode.LIGHT) Color.Black else MaterialTheme.colorScheme.onSurface
                    )
                }
                CupertinoSegmentedControlTab(
                    isSelected = currentTheme == ThemeMode.DARK,
                    onClick = { onThemeChange(ThemeMode.DARK) }
                ) {
                    Text(
                        text = "dark",
                        color = if (currentTheme == ThemeMode.DARK) Color.Black else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            // Sync status section
            Text(
                text = "sync status",
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
                Text("refresh registry", fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            // Registry overview
            Text(
                text = "registry overview",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            RegistryOverviewList(registry = registry)

            // Bottom padding for scroll
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
