package com.joebad.fastbreak.ui.theme

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

/**
 * App accent color used for titles, tabs, and branded UI elements.
 * In the default theme this is purple (light) / pink (dark), stored as Material `onBackground`
 * — not `primary`, which is black/white for controls and metadata.
 */
@Composable
fun accentColor(): Color = MaterialTheme.colorScheme.onBackground

/**
 * Platform-specific system UI configuration (status bar, navigation bar).
 * On Android, this sets the status bar icon colors to match the theme.
 * On iOS, this is a no-op as iOS handles this automatically.
 */
@Composable
expect fun ConfigureSystemUi(themeMode: ThemeMode)

/**
 * Team theme colors to apply to the app.
 * These override the default accent colors.
 */
data class TeamThemeColors(
    val lightPrimary: String?,
    val lightSecondary: String?,
    val darkPrimary: String?,
    val darkSecondary: String?
)

/**
 * Parse a hex color string to a Compose Color.
 */
private fun parseHexColor(hex: String?): Color? {
    if (hex == null) return null
    return try {
        val cleanHex = hex.removePrefix("#")
        Color(("FF$cleanHex").toLong(16))
    } catch (e: Exception) {
        null
    }
}

/**
 * Adjust the brightness of a color.
 * @param brightness Value from -1.0 (darkest) to 1.0 (lightest), 0.0 = no change
 */
private fun Color.adjustBrightness(brightness: Float): Color {
    if (brightness == 0f) return this

    val factor = if (brightness > 0) {
        // Lighten: blend towards white
        1f + brightness
    } else {
        // Darken: reduce RGB values
        1f + brightness
    }

    return if (brightness > 0) {
        // Lighten by blending with white
        Color(
            red = (this.red + (1f - this.red) * brightness).coerceIn(0f, 1f),
            green = (this.green + (1f - this.green) * brightness).coerceIn(0f, 1f),
            blue = (this.blue + (1f - this.blue) * brightness).coerceIn(0f, 1f),
            alpha = this.alpha
        )
    } else {
        // Darken by reducing towards black
        Color(
            red = (this.red * factor).coerceIn(0f, 1f),
            green = (this.green * factor).coerceIn(0f, 1f),
            blue = (this.blue * factor).coerceIn(0f, 1f),
            alpha = this.alpha
        )
    }
}

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    teamColors: TeamThemeColors? = null,
    brightness: ThemeBrightness = ThemeBrightness(),
    colorOverrides: ThemeColorOverrides = ThemeColorOverrides(),
    useSecondaryBackground: UseSecondaryBackground = UseSecondaryBackground(),
    content: @Composable () -> Unit
) {
    val baseColorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
    }

    // Apply team colors if provided, with brightness adjustments
    val colorScheme = if (teamColors != null) {
        when (themeMode) {
            ThemeMode.LIGHT -> {
                val primary = colorOverrides.lightPrimary?.let { parseHexColor(it) }
                    ?: parseHexColor(teamColors.lightPrimary)?.adjustBrightness(brightness.lightPrimary)
                val secondary = colorOverrides.lightSecondary?.let { parseHexColor(it) }
                    ?: parseHexColor(teamColors.lightSecondary)?.adjustBrightness(brightness.lightSecondary)
                val isSecondaryBg = useSecondaryBackground.light && secondary != null
                // Use secondary color as background if enabled
                val background = if (isSecondaryBg) secondary else baseColorScheme.background
                val surface = if (isSecondaryBg) secondary else baseColorScheme.surface
                // Use primary color for text when using secondary as background
                val textColor = if (isSecondaryBg) {
                    primary ?: baseColorScheme.background
                } else {
                    null // Use defaults
                }
                val onBg = textColor ?: (primary ?: baseColorScheme.onBackground)
                val onSurf = textColor ?: baseColorScheme.onSurface
                val primaryColor = primary ?: baseColorScheme.primary
                baseColorScheme.copy(
                    background = background!!,
                    surface = surface!!,
                    surfaceVariant = if (isSecondaryBg) secondary else baseColorScheme.surfaceVariant,
                    onBackground = onBg,
                    onSurface = onSurf,
                    onSurfaceVariant = if (isSecondaryBg) primaryColor else baseColorScheme.onSurfaceVariant,
                    onPrimary = if (isSecondaryBg) secondary!! else baseColorScheme.onPrimary,
                    onSecondary = if (isSecondaryBg) primaryColor else baseColorScheme.onSecondary,
                    onTertiary = if (isSecondaryBg) primaryColor else baseColorScheme.onTertiary,
                    // For FABs: primary icon on secondary background
                    onPrimaryContainer = if (isSecondaryBg) primaryColor else baseColorScheme.onPrimaryContainer,
                    // For selected tabs/pills: secondary (bg color) text on primary background
                    onSecondaryContainer = if (isSecondaryBg) secondary!! else baseColorScheme.onSecondaryContainer,
                    outline = if (isSecondaryBg) primaryColor else baseColorScheme.outline,
                    outlineVariant = if (isSecondaryBg) primaryColor else baseColorScheme.outlineVariant,
                    primary = primaryColor,
                    secondary = secondary ?: baseColorScheme.secondary,
                    tertiary = if (isSecondaryBg) primaryColor else baseColorScheme.tertiary,
                    // FAB background uses secondary (same as app bg for subtle look)
                    primaryContainer = if (isSecondaryBg) secondary else baseColorScheme.primaryContainer,
                    // Selected tabs/pills use primary color background to stand out
                    secondaryContainer = if (isSecondaryBg) primaryColor else baseColorScheme.secondaryContainer,
                    tertiaryContainer = if (isSecondaryBg) secondary else baseColorScheme.tertiaryContainer,
                    onTertiaryContainer = if (isSecondaryBg) primaryColor else baseColorScheme.onTertiaryContainer
                )
            }
            ThemeMode.DARK -> {
                val primary = colorOverrides.darkPrimary?.let { parseHexColor(it) }
                    ?: parseHexColor(teamColors.darkPrimary)?.adjustBrightness(brightness.darkPrimary)
                val secondary = colorOverrides.darkSecondary?.let { parseHexColor(it) }
                    ?: parseHexColor(teamColors.darkSecondary)?.adjustBrightness(brightness.darkSecondary)
                val isSecondaryBg = useSecondaryBackground.dark && secondary != null
                // Use secondary color as background if enabled
                val background = if (isSecondaryBg) secondary else baseColorScheme.background
                val surface = if (isSecondaryBg) secondary else baseColorScheme.surface
                // Use primary color for text when using secondary as background
                val textColor = if (isSecondaryBg) {
                    primary ?: baseColorScheme.background
                } else {
                    null // Use defaults
                }
                val onBg = textColor ?: (primary ?: baseColorScheme.onBackground)
                val onSurf = textColor ?: baseColorScheme.onSurface
                val primaryColor = primary ?: baseColorScheme.primary
                baseColorScheme.copy(
                    background = background!!,
                    surface = surface!!,
                    surfaceVariant = if (isSecondaryBg) secondary else baseColorScheme.surfaceVariant,
                    onBackground = onBg,
                    onSurface = onSurf,
                    onSurfaceVariant = if (isSecondaryBg) primaryColor else baseColorScheme.onSurfaceVariant,
                    onPrimary = if (isSecondaryBg) secondary!! else baseColorScheme.onPrimary,
                    onSecondary = if (isSecondaryBg) primaryColor else baseColorScheme.onSecondary,
                    onTertiary = if (isSecondaryBg) primaryColor else baseColorScheme.onTertiary,
                    // For FABs: primary icon on secondary background
                    onPrimaryContainer = if (isSecondaryBg) primaryColor else baseColorScheme.onPrimaryContainer,
                    // For selected tabs/pills: secondary (bg color) text on primary background
                    onSecondaryContainer = if (isSecondaryBg) secondary!! else baseColorScheme.onSecondaryContainer,
                    outline = if (isSecondaryBg) primaryColor else baseColorScheme.outline,
                    outlineVariant = if (isSecondaryBg) primaryColor else baseColorScheme.outlineVariant,
                    primary = primaryColor,
                    secondary = secondary ?: baseColorScheme.secondary,
                    tertiary = if (isSecondaryBg) primaryColor else baseColorScheme.tertiary,
                    // FAB background uses secondary (same as app bg for subtle look)
                    primaryContainer = if (isSecondaryBg) secondary else baseColorScheme.primaryContainer,
                    // Selected tabs/pills use primary color background to stand out
                    secondaryContainer = if (isSecondaryBg) primaryColor else baseColorScheme.secondaryContainer,
                    tertiaryContainer = if (isSecondaryBg) secondary else baseColorScheme.tertiaryContainer,
                    onTertiaryContainer = if (isSecondaryBg) primaryColor else baseColorScheme.onTertiaryContainer
                )
            }
        }
    } else {
        baseColorScheme
    }

    // Configure system UI (status bar, navigation bar) based on theme
    ConfigureSystemUi(themeMode)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MonospaceTypography,
        content = content
    )
}
