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
    val onAccent: Color,
    val error: Color,
    val onError: Color
)

fun darken(color: Color, factor: Float = 0.8f): Color {
    return Color(
        red = (color.red * factor).coerceIn(0f, 1f),
        green = (color.green * factor).coerceIn(0f, 1f),
        blue = (color.blue * factor).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}

fun lighten(color: Color, factor: Float = 0.2f): Color {
    return Color(
        red = (color.red + (1 - color.red) * factor).coerceIn(0f, 1f),
        green = (color.green + (1 - color.green) * factor).coerceIn(0f, 1f),
        blue = (color.blue + (1 - color.blue) * factor).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}

val LightThemeColors = AppColors(
    background = Color(0xFFF8F3FF),
    text = Color(0xFF4A3957),
    primary = Color(0xFFB8A9E3),
    primaryVariant = Color(0xFFA394D6),
    onPrimary = Color(0xFF4A3957),
    secondary = Color(0xFFD8B4F8),
    onSecondary = Color(0xFF4A3957),
    secondaryVariant = Color(0xFFC8A6F0),
    accent = Color(0xFFA8E6CF),
    onAccent = Color(0xFF2D5A42),
    error = Color(0xFFE8949C),
    onError = Color(0xFF8B2635)
)

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
    error = Color(0xFFFF5252),
    onError = Color(0xFF000000)
)

val LocalColors = staticCompositionLocalOf { LightThemeColors }

@Composable
fun rememberAppColors(isDarkTheme: Boolean): AppColors {
    return remember(isDarkTheme) { if (isDarkTheme) DarkThemeColors else LightThemeColors }
}
