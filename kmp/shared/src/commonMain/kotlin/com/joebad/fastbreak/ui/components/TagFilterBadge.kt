package com.joebad.fastbreak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.Tag

/**
 * Parse hex color string to Compose Color
 * Supports formats: #RGB, #RRGGBB, #AARRGGBB
 */
private fun parseHexColor(hexString: String): Color? {
    return try {
        val hex = hexString.removePrefix("#")
        when (hex.length) {
            6 -> {
                // #RRGGBB
                val r = hex.substring(0, 2).toInt(16)
                val g = hex.substring(2, 4).toInt(16)
                val b = hex.substring(4, 6).toInt(16)
                Color(r, g, b)
            }
            8 -> {
                // #AARRGGBB
                val a = hex.substring(0, 2).toInt(16)
                val r = hex.substring(2, 4).toInt(16)
                val g = hex.substring(4, 6).toInt(16)
                val b = hex.substring(6, 8).toInt(16)
                Color(r, g, b, a)
            }
            3 -> {
                // #RGB - expand to #RRGGBB
                val r = hex.substring(0, 1).repeat(2).toInt(16)
                val g = hex.substring(1, 2).repeat(2).toInt(16)
                val b = hex.substring(2, 3).repeat(2).toInt(16)
                Color(r, g, b)
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * A single tag filter badge that can be selected/unselected.
 * Uses the tag's color with light background and darker text/border.
 */
@Composable
fun TagFilterBadge(
    tag: Tag,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = parseHexColor(tag.color) ?: MaterialTheme.colorScheme.primary

    // Create light background version (20% opacity)
    val lightBackgroundColor = baseColor.copy(alpha = 0.2f)

    // Use the base color for text and border (it's already a vibrant color)
    val darkColor = baseColor

    val backgroundColor = if (isSelected) {
        lightBackgroundColor
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        darkColor
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val textColor = if (isSelected) {
        darkColor
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = tag.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * A non-interactive tag badge for displaying tags in list items.
 * Uses light background and darker text from tag color.
 */
@Composable
fun TagBadge(
    tag: Tag,
    modifier: Modifier = Modifier
) {
    val baseColor = parseHexColor(tag.color) ?: MaterialTheme.colorScheme.secondary

    // Light background (15% opacity for subtle appearance in lists)
    val lightBackgroundColor = baseColor.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .background(
                lightBackgroundColor,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = tag.label,
            color = baseColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Two horizontally scrollable rows of tag filter badges.
 * Left tags wrap content, right tags are scrollable.
 */
@Composable
fun TagFilterBar(
    availableTags: List<Tag>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableTags.isEmpty()) return

    // Group tags by layout
    val leftTags = availableTags.filter { it.layout == "left" }
    val rightTags = availableTags.filter { it.layout == "right" }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left-aligned tags - wrap content (not scrollable)
        if (leftTags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                leftTags.forEach { tag ->
                    TagFilterBadge(
                        tag = tag,
                        isSelected = selectedTags.contains(tag.label),
                        onClick = { onTagToggle(tag.label) }
                    )
                }
            }
        }

        // Right-aligned tags - scrollable, takes remaining space
        if (rightTags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(rightTags) { tag ->
                    TagFilterBadge(
                        tag = tag,
                        isSelected = selectedTags.contains(tag.label),
                        onClick = { onTagToggle(tag.label) }
                    )
                }
            }
        }
    }
}
