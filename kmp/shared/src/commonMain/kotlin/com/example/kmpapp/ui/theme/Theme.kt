package com.example.kmpapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Minimalistic color schemes
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5F5F5),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF666666),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEEEEE),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFF333333),
    onTertiary = Color.White,
    error = Color(0xFFCC0000),
    onError = Color.White,
    errorContainer = Color(0xFFFFEEEE),
    onErrorContainer = Color(0xFFCC0000),
    background = Color.White,
    onBackground = Color(0xFF8B00FF),
    surface = Color.White,
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF333333),
    outline = Color(0xFFDDDDDD),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFAAAAAA),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFCCCCCC),
    onTertiary = Color.Black,
    error = Color(0xFFFF4444),
    onError = Color.Black,
    errorContainer = Color(0xFF331111),
    onErrorContainer = Color(0xFFFF4444),
    background = Color.Black,
    onBackground = Color(0xFFFFB6C1),
    surface = Color(0xFF0A0A0A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF333333),
)

// Monospace typography
private val MonospaceTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    )
)

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MonospaceTypography,
        content = content
    )
}
