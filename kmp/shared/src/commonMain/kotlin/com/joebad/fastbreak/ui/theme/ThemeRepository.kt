package com.joebad.fastbreak.ui.theme

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set

class ThemeRepository(
    private val settings: Settings,
    private val systemThemeDetector: SystemThemeDetector
) {
    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
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

    private fun getSystemTheme(): ThemeMode {
        return if (systemThemeDetector.isSystemInDarkMode()) {
            ThemeMode.DARK
        } else {
            ThemeMode.LIGHT
        }
    }
}
