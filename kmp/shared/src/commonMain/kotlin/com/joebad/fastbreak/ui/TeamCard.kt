package com.joebad.fastbreak.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun TeamCard() {
    val brightGreen = Color(0xFF00C853)
    var selectedRowIndex by remember { mutableStateOf(-1) }

//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .clipToBounds()
//            .padding(8.dp),
//        shape = RectangleShape
//    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Team info section
            SelectableRow(
                text = "Pittsburgh Steelers",
                subText = "10-3, 1st in the East",
                selected = selectedRowIndex == 0,
                onSelect = { selectedRowIndex = 0 },
                highlightColor = brightGreen
            )

            Divider(modifier = Modifier.padding(horizontal = 8.dp))

            Box(
                modifier = Modifier
                    .padding(start = 10.dp, end = 10.dp)
            ) {
                // Second row
                SelectableRow(
                    text = "vs. Baltimore Ravens",
                    subText = "Dec 25, 4:30 PM",
                    selected = selectedRowIndex == 1,
                    onSelect = { selectedRowIndex = 1 },
                    highlightColor = brightGreen
                )
            }

            Divider(modifier = Modifier.padding(horizontal = 8.dp))

            // Mini cards section
            Text(
                text = "Player Stats",
                style = MaterialTheme.typography.subtitle2,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            LazyRow(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                val items = listOf("QB Rating: 105.3", "Rushing: 1,506 yds", "Receiving: 2,104 yds", "Defense: 18 INT", "Touchdowns: 32")
                items(items.indices.toList()) { index ->
                    val cardIndex = index + 2 // Start at 2 since we used 0 and 1 for the rows
                    MiniCard(
                        text = items[index],
                        isSelected = selectedRowIndex == cardIndex,
                        onSelect = { selectedRowIndex = cardIndex },
                        highlightColor = brightGreen
                    )
                }
            }
        }
    }
//}

@Composable
fun SelectableRow(
    text: String,
    subText: String,
    selected: Boolean,
    onSelect: () -> Unit,
    highlightColor: Color
) {
    println("Selected state: $selected")

    // Animate both elevation and scale
    val animatedElevation by animateDpAsState(
        targetValue = if (selected) 10.dp else 2.dp,
        animationSpec = tween(durationMillis = 300)
    )

    // Add scale animation
    val animatedScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 300)
    )

    println("Current animated elevation: $animatedElevation")

    // For Material 3
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .zIndex(1f)
            // Apply scale transformation without affecting layout
            .graphicsLayer(
                scaleX = animatedScale,
                scaleY = animatedScale,
                // This keeps the scaling centered on the component
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            )
            .zIndex(1f)
            .clickable { onSelect() }
            .border(
                width = 2.dp,
                color = if (selected) highlightColor else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .shadow(
                elevation = animatedElevation,
                spotColor = Color.Black,
                ambientColor = Color.Black,
                shape = RoundedCornerShape(4.dp)
            ),
        elevation = animatedElevation,
    ) {
        // Card content remains the same
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.LightGray)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Icon",
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = text, style = MaterialTheme.typography.subtitle1)
                Text(
                    text = subText,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun MiniCard(
    text: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    highlightColor: Color
) {
    val animatedElevation by animateDpAsState(targetValue = if (isSelected) 10.dp else 2.dp)

    Card(
        modifier = Modifier
            .width(100.dp)
            .height(60.dp)
            .clickable(onClick = onSelect)
            .shadow(
                elevation = animatedElevation,
                shape = RoundedCornerShape(4.dp),
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black
            ),
        elevation = animatedElevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 2.dp,
                    color = if (isSelected) highlightColor else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.caption,
                maxLines = 2
            )
        }
    }
}
