// In shared/src/iosMain/kotlin/com/your/package/font/FontLoader.kt
package com.joebad.fastbreak

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import io.github.alexzhirkevich.cupertino.toUIColor
import kotbase.ext.toByteArray
import platform.Foundation.NSData
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

@Composable
actual fun ApplySystemBarsColor(color: Color) {
    val window = UIApplication.sharedApplication.keyWindow
    window?.rootViewController?.view?.backgroundColor = color.toUIColor()

    // Optional: If you want to set navigation bar appearance as well
    window?.rootViewController?.navigationController?.navigationBar?.barTintColor = color.toUIColor()
}

// Convert Compose Color to UIColor
fun Color.toUIColor(): platform.UIKit.UIColor {
    return platform.UIKit.UIColor(
        red = red.toDouble(),
        green = green.toDouble(),
        blue = blue.toDouble(),
        alpha = alpha.toDouble()
    )
}