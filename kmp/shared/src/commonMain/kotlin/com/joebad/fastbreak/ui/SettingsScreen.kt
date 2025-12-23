package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.navigation.SettingsComponent
import com.joebad.fastbreak.platform.AppVersion
import com.joebad.fastbreak.ui.container.RegistryState
import com.joebad.fastbreak.ui.diagnostics.SyncStatusRow
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import com.joebad.fastbreak.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    component: SettingsComponent,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    diagnostics: DiagnosticsInfo,
    onRefreshRegistry: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("settings") },
                navigationIcon = {
                    IconButton(onClick = component.onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme selector section
            Column {
                Text(
                    text = "theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
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
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Sync status section
            Column {
                Text(
                    text = "sync status",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                SyncStatusRow(diagnostics = diagnostics)

                Spacer(modifier = Modifier.height(12.dp))

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
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Version info section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "version",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "v${AppVersion.versionName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "build ${AppVersion.buildNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
