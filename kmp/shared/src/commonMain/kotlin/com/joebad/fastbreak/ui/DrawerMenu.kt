package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import com.joebad.fastbreak.ui.diagnostics.RegistryOverviewList
import com.joebad.fastbreak.ui.diagnostics.SyncStatusRow
import com.joebad.fastbreak.ui.theme.ThemeMode
// Cupertino library removed due to Compose version incompatibility
// import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControl
// import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlTab

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
            // Sync status section
            Text(
                text = "sync status",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            SyncStatusRow(diagnostics = diagnostics)

            Spacer(modifier = Modifier.height(12.dp))

            // Refresh button - disabled while syncing, but enabled if there's an error
            OutlinedButton(
                onClick = onRefreshRegistry,
                enabled = !diagnostics.isSyncing || diagnostics.lastError != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "refresh registry",
                    fontFamily = FontFamily.Monospace
                )
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

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            // Theme selector at bottom
            Text(
                text = "theme",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Material3 SegmentedButton replacement for Cupertino
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = currentTheme == ThemeMode.LIGHT,
                    onClick = { onThemeChange(ThemeMode.LIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(text = "light")
                }
                SegmentedButton(
                    selected = currentTheme == ThemeMode.DARK,
                    onClick = { onThemeChange(ThemeMode.DARK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(text = "dark")
                }
            }

            // Bottom padding for scroll
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
