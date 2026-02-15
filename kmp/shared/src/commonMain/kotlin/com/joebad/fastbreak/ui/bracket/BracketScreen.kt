package com.joebad.fastbreak.ui.bracket

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Screen that displays the 3D bracket visualization using Filament.
 */
@Composable
fun BracketScreen(
    onNavigateBack: () -> Unit
) {
    val bracketData = remember { createFakeBracketData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bracketData.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        FilamentBracketView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            bracketData = bracketData
        )
    }
}
