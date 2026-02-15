package com.joebad.fastbreak.ui.bracket

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific 3D bracket visualization powered by Google Filament.
 * Renders the bracket data as 3D geometry floating in space.
 */
@Composable
expect fun FilamentBracketView(
    modifier: Modifier = Modifier,
    bracketData: BracketData
)
