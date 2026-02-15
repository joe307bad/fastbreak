package com.joebad.fastbreak.ui.bracket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * iOS implementation of the 3D bracket view.
 *
 * Filament's iOS API is C++ and requires an Objective-C++ bridge to be
 * callable from Kotlin/Native. The Filament pod has been added to the
 * iOS Podfile. To complete the native integration:
 *
 * 1. Create an Objective-C++ UIView subclass (FilamentBracketUIView) in the
 *    iOS app that initializes Filament with Metal backend and renders the bracket.
 * 2. Expose it via a Swift-compatible factory.
 * 3. Replace this placeholder with UIKitView wrapping the native Filament view.
 *
 * For now, this renders a 2D preview of the bracket structure as a placeholder.
 */
@Composable
actual fun FilamentBracketView(
    modifier: Modifier,
    bracketData: BracketData
) {
    // 2D placeholder showing the bracket structure until native Filament bridge is wired
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
            text = "Filament pod installed. Native Metal bridge pending.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Render a simple 2D bracket preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bracketData.rounds.forEach { round ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = round.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFAAAAAA)
                    )
                    round.matchups.forEach { matchup ->
                        Column(
                            modifier = Modifier
                                .background(Color(0xFF2A2A3A))
                                .padding(8.dp)
                        ) {
                            val t1Color = if (matchup.winner == 1) Color(0xFF4CAF50) else Color.White
                            val t2Color = if (matchup.winner == 2) Color(0xFF4CAF50) else Color.White
                            Text(
                                text = "(${matchup.team1.seed}) ${matchup.team1.name} ${matchup.team1.score ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = t1Color
                            )
                            Text(
                                text = "(${matchup.team2.seed}) ${matchup.team2.name} ${matchup.team2.score ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = t2Color
                            )
                        }
                    }
                }
            }
        }
    }
}
