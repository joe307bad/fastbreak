package com.joebad.fastbreak

import Theme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import io.github.alexzhirkevich.cupertino.toUIColor
import kotbase.ext.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIApplication
import platform.UIKit.navigationController

actual fun getPlatform(): Platform = IOSPlatform()

private class IOSPlatform : Platform {
    override val name: String = "iOS"
}

actual fun onApplicationStartPlatformSpecific() {
}

class IosFontLoader : FontLoader {
    override fun loadFont(fontName: String): FontFamily {
        val bundle = platform.Foundation.NSBundle.mainBundle

        val fontFilename = fontName.split("/").last()
        val path = bundle.pathForResource(fontFilename.removeSuffix(".otf"), "otf")
            ?: throw Exception("Font not found: $fontName")

        return FontFamily(
            Font(
                identity = fontName,
                data = NSData.dataWithContentsOfFile(path)?.toByteArray()
                    ?: throw Exception("Failed to load font data from $path"),
                weight = androidx.compose.ui.text.font.FontWeight.Normal,
                style = androidx.compose.ui.text.font.FontStyle.Normal
            )
        )
    }
}

actual fun createFontLoader(): FontLoader {
    return IosFontLoader()
}

@Composable
actual fun ApplySystemBarsColor(color: Color) {
    val window = UIApplication.sharedApplication.keyWindow
    window?.rootViewController?.view?.backgroundColor = color.toUIColor()

    window?.rootViewController?.navigationController?.navigationBar?.barTintColor = color.toUIColor()
}

class IosThemePreference : ThemePreference {
    private val THEME_KEY = "theme"

    override suspend fun saveTheme(theme: Theme) {
        NSUserDefaults.standardUserDefaults.setInteger(theme.ordinal.toLong(), THEME_KEY)
    }

    override suspend fun getTheme(): Theme {
        val themeOrdinal = NSUserDefaults.standardUserDefaults.integerForKey(THEME_KEY).toInt()
        return Theme.values().getOrElse(themeOrdinal) { Theme.Dark }
    }
}