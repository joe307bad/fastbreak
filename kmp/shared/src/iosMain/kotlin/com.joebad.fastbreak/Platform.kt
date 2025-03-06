// In shared/src/iosMain/kotlin/com/your/package/font/FontLoader.kt
package com.joebad.fastbreak

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import kotbase.ext.toByteArray
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

actual fun getPlatform(): Platform = IOSPlatform()

private class IOSPlatform : Platform {
    override val name: String = "iOS"
}

actual fun onApplicationStartPlatformSpecific() {
}

class IosFontLoader : FontLoader {
    override fun loadFont(fontName: String): FontFamily {
        val bundle = platform.Foundation.NSBundle.mainBundle

        // Try to find the font with just the name (without path)
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