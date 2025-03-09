package com.joebad.fastbreak

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
                // Load from assets
                context.assets.open("font/$fontName.otf").use { input ->
                    fontFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                try {
                    // Fallback: try to load as a resource in the common module
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

        // Now we have the font file, use the File constructor
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
        // Set status bar and navigation bar colors
        it.statusBarColor = color.toArgb()
        it.navigationBarColor = color.toArgb()

        // Optional: Adjust the icons for dark or light mode
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
    }
}
