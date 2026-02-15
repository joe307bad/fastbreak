package com.joebad.fastbreak.ui.bracket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp

/**
 * iOS implementation of the 3D bracket view using Google Filament with Metal backend.
 *
 * The native FilamentBracketUIView (Objective-C++) is created in the iOS app and
 * injected into the shared module via [BracketViewFactory]. If the factory is not
 * set, a 2D fallback is shown.
 */
@Composable
actual fun FilamentBracketView(
    modifier: Modifier,
    bracketData: BracketData
) {
    val factory = BracketViewFactory.createView

    if (factory != null) {
        UIKitView(
            factory = { factory() },
            modifier = modifier
        )
    } else {
        // Fallback: 2D bracket preview when native Filament view is unavailable
        Column(
            modifier = modifier
                .background(Color(0xFF1A1A24))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "3D Bracket (Filament - iOS)",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Native Filament view not available",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF888888)
            )
        }
    }
}
