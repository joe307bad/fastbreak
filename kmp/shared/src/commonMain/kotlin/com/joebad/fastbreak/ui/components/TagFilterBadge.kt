package com.joebad.fastbreak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
 * Darken a color by reducing its RGB values by a factor.
 * @param factor Amount to darken (0.0 = no change, 1.0 = black)
 */
private fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * A single tag filter badge that can be selected/unselected.
 * In + modes (light+/dark+), uses a darkened version of the tag's color as background.
 * In normal modes, uses transparent/surface background.
 */
@Composable
fun TagFilterBadge(
    tag: Tag,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = parseHexColor(tag.color) ?: MaterialTheme.colorScheme.primary

    // Detect + mode: surfaceVariant equals background in + modes
    val isSecondaryBgMode = MaterialTheme.colorScheme.surfaceVariant == MaterialTheme.colorScheme.background

    // In + mode, use darkened tag color as background; in normal mode, use surface/transparent
    val backgroundColor = if (isSelected) {
        if (isSecondaryBgMode) baseColor.darken(0.7f) else MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        baseColor
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val textColor = if (isSelected) {
        baseColor
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
 * In + modes (light+/dark+), uses a darkened version of the tag's color as background.
 * In normal modes, uses transparent background with colored text.
 */
@Composable
fun TagBadge(
    tag: Tag,
    modifier: Modifier = Modifier
) {
    val baseColor = parseHexColor(tag.color) ?: MaterialTheme.colorScheme.secondary

    // Detect + mode: surfaceVariant equals background in + modes
    val isSecondaryBgMode = MaterialTheme.colorScheme.surfaceVariant == MaterialTheme.colorScheme.background

    // In + mode, use darkened tag color as background; in normal mode, use light transparent
    val backgroundColor = if (isSecondaryBgMode) {
        baseColor.darken(0.7f)
    } else {
        baseColor.copy(alpha = 0.15f)
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(1.dp, if (isSecondaryBgMode) baseColor else Color.Transparent, RoundedCornerShape(6.dp))
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
 * Horizontally scrollable row of tag filter badges.
 * Left-layout tags appear first, followed by right-layout tags.
 */
@Composable
fun TagFilterBar(
    availableTags: List<Tag>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableTags.isEmpty()) return

    // Group tags by layout - left tags first, then right tags
    val leftTags = availableTags.filter { it.layout == "left" }
    val rightTags = availableTags.filter { it.layout == "right" }
    val orderedTags = leftTags + rightTags

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(orderedTags) { tag ->
            TagFilterBadge(
                tag = tag,
                isSelected = selectedTags.contains(tag.label),
                onClick = { onTagToggle(tag.label) }
            )
        }
    }
}
