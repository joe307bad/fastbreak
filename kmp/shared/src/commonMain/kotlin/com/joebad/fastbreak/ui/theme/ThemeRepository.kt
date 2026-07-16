package com.joebad.fastbreak.ui.theme

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set

/**
 * Represents a selected team theme with sport and team code.
 */
data class SelectedTeamTheme(
    val sport: String,
    val teamCode: String
)

/**
 * Wrapper class for team theme to support null values with MutableValue.
 */
data class OptionalTeamTheme(
    val theme: SelectedTeamTheme? = null
)

/**
 * Brightness adjustments for team theme colors.
 * Values range from -1.0 (darkest) to 1.0 (lightest), with 0.0 being no adjustment.
 */
data class ThemeBrightness(
    val lightPrimary: Float = 0f,
    val lightSecondary: Float = 0f,
    val darkPrimary: Float = 0f,
    val darkSecondary: Float = 0f
)

/**
 * Enum for the four color slots in a team theme.
 */
enum class ColorSlot {
    LIGHT_PRIMARY,
    LIGHT_SECONDARY,
    DARK_PRIMARY,
    DARK_SECONDARY
}

/**
 * Absolute per-slot color overrides picked via the color picker.
 * A non-null hex string ("#RRGGBB") replaces the team's color for that slot.
 */
data class ThemeColorOverrides(
    val lightPrimary: String? = null,
    val lightSecondary: String? = null,
    val darkPrimary: String? = null,
    val darkSecondary: String? = null
)

/**
 * Whether to use the team's secondary color as background instead of default white/black.
 */
data class UseSecondaryBackground(
    val light: Boolean = false,
    val dark: Boolean = false
)

class ThemeRepository(
    private val settings: Settings,
    private val systemThemeDetector: SystemThemeDetector
) {
    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TEAM_THEME = "team_theme"
        private const val KEY_BRIGHTNESS_LIGHT_PRIMARY = "brightness_light_primary"
        private const val KEY_BRIGHTNESS_LIGHT_SECONDARY = "brightness_light_secondary"
        private const val KEY_BRIGHTNESS_DARK_PRIMARY = "brightness_dark_primary"
        private const val KEY_BRIGHTNESS_DARK_SECONDARY = "brightness_dark_secondary"
        private const val KEY_COLOR_OVERRIDE_LIGHT_PRIMARY = "color_override_light_primary"
        private const val KEY_COLOR_OVERRIDE_LIGHT_SECONDARY = "color_override_light_secondary"
        private const val KEY_COLOR_OVERRIDE_DARK_PRIMARY = "color_override_dark_primary"
        private const val KEY_COLOR_OVERRIDE_DARK_SECONDARY = "color_override_dark_secondary"
        private const val KEY_USE_SECONDARY_BG_LIGHT = "use_secondary_bg_light"
        private const val KEY_USE_SECONDARY_BG_DARK = "use_secondary_bg_dark"
        private const val VALUE_LIGHT = "light"
        private const val VALUE_DARK = "dark"
    }

    fun getSavedTheme(): ThemeMode? {
        val savedValue = settings.getStringOrNull(KEY_THEME_MODE)
        return when (savedValue) {
            VALUE_LIGHT -> ThemeMode.LIGHT
            VALUE_DARK -> ThemeMode.DARK
            else -> null
        }
    }

    fun getInitialTheme(): ThemeMode {
        return getSavedTheme() ?: getSystemTheme()
    }

    fun saveTheme(theme: ThemeMode) {
        val value = when (theme) {
            ThemeMode.LIGHT -> VALUE_LIGHT
            ThemeMode.DARK -> VALUE_DARK
        }
        settings[KEY_THEME_MODE] = value
    }

    /**
     * Get the saved team theme, if any.
     * Returns null if no team theme is selected (using default app theme).
     */
    fun getSelectedTeamTheme(): SelectedTeamTheme? {
        val savedValue = settings.getStringOrNull(KEY_TEAM_THEME) ?: return null
        val parts = savedValue.split(":")
        return if (parts.size == 2) {
            SelectedTeamTheme(sport = parts[0], teamCode = parts[1])
        } else {
            null
        }
    }

    /**
     * Save a team theme selection.
     * Pass null to clear the team theme and use default app theme.
     */
    fun saveTeamTheme(teamTheme: SelectedTeamTheme?) {
        if (teamTheme == null) {
            settings.remove(KEY_TEAM_THEME)
        } else {
            settings[KEY_TEAM_THEME] = "${teamTheme.sport}:${teamTheme.teamCode}"
        }
    }

    /**
     * Get the saved brightness adjustments for team theme colors.
     */
    fun getThemeBrightness(): ThemeBrightness {
        return ThemeBrightness(
            lightPrimary = settings.getFloat(KEY_BRIGHTNESS_LIGHT_PRIMARY, 0f),
            lightSecondary = settings.getFloat(KEY_BRIGHTNESS_LIGHT_SECONDARY, 0f),
            darkPrimary = settings.getFloat(KEY_BRIGHTNESS_DARK_PRIMARY, 0f),
            darkSecondary = settings.getFloat(KEY_BRIGHTNESS_DARK_SECONDARY, 0f)
        )
    }

    /**
     * Save brightness adjustment for a specific color slot.
     */
    fun saveBrightness(slot: ColorSlot, value: Float) {
        val key = when (slot) {
            ColorSlot.LIGHT_PRIMARY -> KEY_BRIGHTNESS_LIGHT_PRIMARY
            ColorSlot.LIGHT_SECONDARY -> KEY_BRIGHTNESS_LIGHT_SECONDARY
            ColorSlot.DARK_PRIMARY -> KEY_BRIGHTNESS_DARK_PRIMARY
            ColorSlot.DARK_SECONDARY -> KEY_BRIGHTNESS_DARK_SECONDARY
        }
        settings.putFloat(key, value)
    }

    /**
     * Clear all brightness adjustments (when selecting a new team).
     */
    fun clearBrightness() {
        settings.remove(KEY_BRIGHTNESS_LIGHT_PRIMARY)
        settings.remove(KEY_BRIGHTNESS_LIGHT_SECONDARY)
        settings.remove(KEY_BRIGHTNESS_DARK_PRIMARY)
        settings.remove(KEY_BRIGHTNESS_DARK_SECONDARY)
    }

    private fun colorOverrideKey(slot: ColorSlot): String = when (slot) {
        ColorSlot.LIGHT_PRIMARY -> KEY_COLOR_OVERRIDE_LIGHT_PRIMARY
        ColorSlot.LIGHT_SECONDARY -> KEY_COLOR_OVERRIDE_LIGHT_SECONDARY
        ColorSlot.DARK_PRIMARY -> KEY_COLOR_OVERRIDE_DARK_PRIMARY
        ColorSlot.DARK_SECONDARY -> KEY_COLOR_OVERRIDE_DARK_SECONDARY
    }

    /**
     * Get the saved absolute color overrides for team theme colors.
     */
    fun getThemeColorOverrides(): ThemeColorOverrides {
        return ThemeColorOverrides(
            lightPrimary = settings.getStringOrNull(KEY_COLOR_OVERRIDE_LIGHT_PRIMARY),
            lightSecondary = settings.getStringOrNull(KEY_COLOR_OVERRIDE_LIGHT_SECONDARY),
            darkPrimary = settings.getStringOrNull(KEY_COLOR_OVERRIDE_DARK_PRIMARY),
            darkSecondary = settings.getStringOrNull(KEY_COLOR_OVERRIDE_DARK_SECONDARY)
        )
    }

    /**
     * Save (or clear, when [hex] is null) an absolute color override for a slot.
     */
    fun saveColorOverride(slot: ColorSlot, hex: String?) {
        val key = colorOverrideKey(slot)
        if (hex == null) {
            settings.remove(key)
        } else {
            settings[key] = hex
        }
    }

    /**
     * Clear all color overrides (when selecting a new team).
     */
    fun clearColorOverrides() {
        settings.remove(KEY_COLOR_OVERRIDE_LIGHT_PRIMARY)
        settings.remove(KEY_COLOR_OVERRIDE_LIGHT_SECONDARY)
        settings.remove(KEY_COLOR_OVERRIDE_DARK_PRIMARY)
        settings.remove(KEY_COLOR_OVERRIDE_DARK_SECONDARY)
    }

    /**
     * Get whether to use secondary color as background.
     */
    fun getUseSecondaryBackground(): UseSecondaryBackground {
        return UseSecondaryBackground(
            light = settings.getBoolean(KEY_USE_SECONDARY_BG_LIGHT, false),
            dark = settings.getBoolean(KEY_USE_SECONDARY_BG_DARK, false)
        )
    }

    /**
     * Toggle using secondary color as background for a theme mode.
     */
    fun toggleSecondaryBackground(themeMode: ThemeMode) {
        val key = when (themeMode) {
            ThemeMode.LIGHT -> KEY_USE_SECONDARY_BG_LIGHT
            ThemeMode.DARK -> KEY_USE_SECONDARY_BG_DARK
        }
        val current = settings.getBoolean(key, false)
        settings.putBoolean(key, !current)
    }

    /**
     * Clear secondary background settings (when selecting a new team).
     */
    fun clearSecondaryBackground() {
        settings.remove(KEY_USE_SECONDARY_BG_LIGHT)
        settings.remove(KEY_USE_SECONDARY_BG_DARK)
    }

    private fun getSystemTheme(): ThemeMode {
        return if (systemThemeDetector.isSystemInDarkMode()) {
            ThemeMode.DARK
        } else {
            ThemeMode.LIGHT
        }
    }
}
