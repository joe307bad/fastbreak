package com.joebad.fastbreak

import Theme
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.File

actual fun getPlatform(): Platform = AndroidPlatform()

private class AndroidPlatform : Platform {
    override val name: String = "Android"
}

actual fun onApplicationStartPlatformSpecific() {
}


class AndroidFontLoader(private val context: Context) : FontLoader {
    override fun loadFont(fontName: String): FontFamily {
        val cacheDir = context.cacheDir
        val fontFile = File(cacheDir, "$fontName.otf")

        if (!fontFile.exists()) {
            try {
                context.assets.open("font/$fontName.otf").use { input ->
                    fontFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                try {
                    val inputStream = this::class.java.classLoader?.getResourceAsStream("font/$fontName.otf")
                        ?: throw Exception("Font not found: $fontName.otf")
                    fontFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to load font: $fontName.otf", e)
                }
            }
        }

        return FontFamily(
            Font(
                file = fontFile,
                weight = FontWeight.Normal,
                style = FontStyle.Normal
            )
        )
    }
}

private lateinit var appContext: Context

fun initFontLoader(context: Context) {
    appContext = context.applicationContext
}

actual fun createFontLoader(): FontLoader {
    if (!::appContext.isInitialized) {
        throw IllegalStateException("Font loader not initialized. Call initFontLoader(context) first.")
    }
    return AndroidFontLoader(appContext)
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
actual fun ApplySystemBarsColor(color: Color) {
    val context = LocalContext.current
    val window = (context as? android.app.Activity)?.window
    window?.let {
        it.statusBarColor = color.toArgb()
        it.navigationBarColor = color.toArgb()

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
    }
}

private val Context.dataStore by preferencesDataStore(name = "theme_prefs")

class AndroidThemePreference(private val context: Context) : ThemePreference {
    private val THEME_KEY = intPreferencesKey("theme")

    override suspend fun saveTheme(theme: Theme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.ordinal
        }
    }

    override suspend fun getTheme(): Theme {
        val preferences = context.dataStore.data.first()
        return Theme.values()[preferences[THEME_KEY] ?: Theme.Dark.ordinal]
    }
}
