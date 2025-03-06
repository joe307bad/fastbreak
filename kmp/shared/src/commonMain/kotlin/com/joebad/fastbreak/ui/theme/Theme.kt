package com.joebad.fastbreak.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val text: Color,
    val primary: Color,
    val primaryVariant: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryVariant: Color,
    val accent: Color,
    val onAccent: Color
)

fun darken(color: Color, factor: Float = 0.8f): Color {
    return Color(
        red = (color.red * factor).coerceIn(0f, 1f),
        green = (color.green * factor).coerceIn(0f, 1f),
        blue = (color.blue * factor).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}

// Light Mode: Soft pastel pinks and purples
val LightThemeColors = AppColors(
    background = Color(0xFFF8E8F8),
    text = Color(0xFF4A2040),
    primary = darken(Color(0xFFFFA6C9), 0.7f),
    primaryVariant = Color(0xFFFF78A9),
    onPrimary = Color(0xFF4A2040),
    secondary = Color(0xFFD8B4F8),
    onSecondary = Color(0xFF4A2040),
    secondaryVariant = Color(0xFFC084F5),
    accent = Color(0xFFFFC75F),
    onAccent = darken(Color(0xFFFFC75F), 0.5f)
)

// Dark Mode: Indigo/Violet-based with cool greys
val DarkThemeColors = AppColors(
    background = darken(Color(0xFF242038), 0.5f),
    text = Color(0xFFE0C3FC),
    primary = darken(Color(0xFF6A0DAD), 0.5f),
    primaryVariant = Color(0xFF4A0072),
    onPrimary = darken(Color(0xFFF3E5F5), 0.9f),
    secondary = Color(0xFF8A2BE2),
    onSecondary = Color(0xFFF3E5F5),
    secondaryVariant = Color(0xFF6D1B7B),
    accent = darken( Color(0xFF00E5FF), 0.8f),
    onAccent = darken(Color(0xFF6A0DAD), 0.5f),
)


// Local composition holder for colors
val LocalColors = staticCompositionLocalOf { LightThemeColors }

@Composable
fun rememberAppColors(isDarkTheme: Boolean): AppColors {
    return remember(isDarkTheme) { if (isDarkTheme) DarkThemeColors else LightThemeColors }
}
