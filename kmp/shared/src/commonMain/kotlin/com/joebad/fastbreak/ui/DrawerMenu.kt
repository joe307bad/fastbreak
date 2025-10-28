package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.theme.ThemeMode
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControl
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlTab

@Composable
fun DrawerMenu(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "settings",
                style = MaterialTheme.typography.titleLarge,
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
        }
    }
}
