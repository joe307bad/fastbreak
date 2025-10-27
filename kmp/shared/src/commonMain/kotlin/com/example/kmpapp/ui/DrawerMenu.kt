package com.example.kmpapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kmpapp.ui.theme.ThemeMode
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControl
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlTab

@Composable
fun DrawerMenu(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

            // Theme selector
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleMedium,
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
                        text = "Light",
                        color = if (currentTheme == ThemeMode.LIGHT) Color.Black else MaterialTheme.colorScheme.onSurface
                    )
                }
                CupertinoSegmentedControlTab(
                    isSelected = currentTheme == ThemeMode.DARK,
                    onClick = { onThemeChange(ThemeMode.DARK) }
                ) {
                    Text(
                        text = "Dark",
                        color = if (currentTheme == ThemeMode.DARK) Color.Black else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
