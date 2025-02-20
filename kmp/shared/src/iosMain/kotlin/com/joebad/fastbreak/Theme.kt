package com.joebad.fastbreak

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveTheme
import io.github.alexzhirkevich.cupertino.adaptive.CupertinoThemeSpec
import io.github.alexzhirkevich.cupertino.adaptive.MaterialThemeSpec
import io.github.alexzhirkevich.cupertino.adaptive.Theme
import io.github.alexzhirkevich.cupertino.theme.darkColorScheme
import io.github.alexzhirkevich.cupertino.theme.lightColorScheme
import platform.UIKit.UIViewController

private val LightThemeColors = lightColorScheme(

    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
)
private val DarkThemeColors = darkColorScheme(

    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
)

data class ThemeState(val useDarkTheme: DarkThemeState, val isCupertino: Boolean)

val ThemeStateDefault get() = ThemeState(DarkThemeState.SYSTEM, isCupertino = false)

enum class DarkThemeState {
    DARK,
    LIGHT,
    SYSTEM
}

@Composable
fun FastbreakTheme(
    themeState: ThemeState = ThemeStateDefault,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeState.useDarkTheme) {
        DarkThemeState.DARK -> true
        DarkThemeState.LIGHT -> false
        DarkThemeState.SYSTEM -> isSystemInDarkTheme()
    }
    AdaptiveTheme(
        material =
        MaterialThemeSpec(
            colorScheme = if (darkTheme) {
                DarkThemeColors
            } else {
                LightThemeColors
            },
            typography = typography,
            shapes = shapes,
        ),
        cupertino =
        CupertinoThemeSpec(
            colorScheme = if (darkTheme) {
                darkColorScheme()
            } else {
                lightColorScheme()
            },
        ),
        target = if (themeState.isCupertino) Theme.Cupertino else Theme.Material3,
        content = content
    )
}

fun isCupertinoDefault() {}

data class CupertinoButton(val render: () -> UIViewController)

// Function that returns a UIViewController
fun CupertinoButtonViewController(): UIViewController =
    ComposeUIViewController { IconButtonWithText() }

// Correct way to initialize CupertinoButton
val CupertinoButtonDefault get() = CupertinoButton(::CupertinoButtonViewController)

