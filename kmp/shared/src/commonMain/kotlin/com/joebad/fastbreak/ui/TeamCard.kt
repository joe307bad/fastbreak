package com.joebad.fastbreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TeamCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = 4.dp // Subtle elevation
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top third
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Placeholder photo
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.LightGray)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Menu")
                }

                // Center: Title and subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pittsburgh Steelers",
                        style = MaterialTheme.typography.h6
                    )
                    Text(
                        text = "10-3, 1st in the East",
                        style = MaterialTheme.typography.subtitle2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Right: Selection box
                Checkbox(
                    checked = false,
                    onCheckedChange = { /* Handle selection */ }
                )
            }

            Divider()

            // Middle third (same as top)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Placeholder photo
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.LightGray)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Team Logo",
                        tint = Color.Gray
                    )
                }

                // Center: Title and subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pittsburgh Steelers",
                        style = MaterialTheme.typography.h6
                    )
                    Text(
                        text = "10-3, 1st in the East",
                        style = MaterialTheme.typography.subtitle2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Right: Selection box
                Checkbox(
                    checked = false,
                    onCheckedChange = { /* Handle selection */ }
                )
            }

            Divider()

            // Bottom third: Horizontally scrollable mini cards
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(10) { index ->
                    MiniCard(index)
                }
            }
        }
    }
}

@Composable
private fun MiniCard(index: Int) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(80.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Lorem text (a couple of words)
            Text(
                text = when (index % 5) {
                    0 -> "Lorem ipsum"
                    1 -> "Dolor sit"
                    2 -> "Amet consectetur"
                    3 -> "Adipiscing elit"
                    else -> "Sed do"
                },
                style = MaterialTheme.typography.caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Selectable checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Checkbox(
                    checked = false,
                    onCheckedChange = { /* Handle selection */ },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}