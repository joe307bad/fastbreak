package com.joebad.fastbreak.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage

class IOSImageExporter : ImageExporter {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun shareImage(bitmap: ImageBitmap, title: String) {
        try {
            // Convert ImageBitmap to Skia Bitmap and encode to JPEG (no transparency, ensures opaque white background)
            val skiaBitmap = bitmap.asSkiaBitmap()
            val encodedData = org.jetbrains.skia.Image.makeFromBitmap(skiaBitmap)
                .encodeToData(EncodedImageFormat.JPEG, quality = 95)
                ?: throw IllegalStateException("Failed to encode bitmap to JPEG")

            // Convert Skia Data to NSData
            val bytes = encodedData.bytes
            val nsData = NSData.create(
                bytes = bytes.usePinned { pinned ->
                    pinned.addressOf(0)
                },
                length = bytes.size.toULong()
            )

            // Create UIImage from NSData
            val uiImage = UIImage.imageWithData(nsData)
                ?: throw IllegalStateException("Failed to create UIImage from data")

            // Create activity view controller for sharing
            val activityViewController = UIActivityViewController(
                activityItems = listOf(uiImage),
                applicationActivities = null
            )

            // Get the root view controller and present the share sheet
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            rootViewController?.presentViewController(
                viewControllerToPresent = activityViewController,
                animated = true,
                completion = null
            )

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}

actual fun getImageExporter(): ImageExporter {
    return IOSImageExporter()
}

actual fun addTitleToBitmap(bitmap: ImageBitmap, title: String, isDarkTheme: Boolean, textColor: Int): ImageBitmap {
    val skiaBitmap = bitmap.asSkiaBitmap()

    // Calculate title height based on text size - make it bigger and more visible
    val titleTextSize = 64f  // Increased from 48f
    val titlePadding = 48f   // Increased from 32f
    val titleHeight = (titleTextSize + titlePadding * 2).toInt()

    println("📸 ========================================")
    println("📸 addTitleToBitmap - title: '$title', isDarkTheme: $isDarkTheme")
    println("📸 Original bitmap size: ${skiaBitmap.width}x${skiaBitmap.height}")
    println("📸 Title height: $titleHeight, text size: $titleTextSize")
    println("📸 ========================================")

    // Create a new surface with extra height for title
    val newWidth = skiaBitmap.width
    val newHeight = skiaBitmap.height + titleHeight
    val surface = Surface.makeRasterN32Premul(newWidth, newHeight)
    val canvas = surface.canvas

    // Use theme colors: dark background with white text, or white background with black text
    val backgroundColor = if (isDarkTheme) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    println("📸 Background color: ${backgroundColor.toUInt().toString(16)}, Text color: ${textColor.toUInt().toString(16)}")

    // Step 1: Draw full background first (this ensures the entire image has the correct background)
    val backgroundPaint = Paint().apply {
        color = backgroundColor
    }
    canvas.drawRect(
        org.jetbrains.skia.Rect.makeXYWH(0f, 0f, newWidth.toFloat(), newHeight.toFloat()),
        backgroundPaint
    )
    println("📸 Background drawn")

    // Step 2: Draw title text in the title area BEFORE drawing the chart
    // Create font with typewriter/courier typeface (normal weight, not bold)
    val typeface = org.jetbrains.skia.FontMgr.default.matchFamilyStyle("Courier", org.jetbrains.skia.FontStyle.NORMAL)
    val font = Font(typeface, titleTextSize)

    // Create text paint with explicit color setting
    val textPaint = Paint()
    textPaint.color = textColor
    textPaint.isAntiAlias = true

    println("📸 Text paint setup:")
    println("📸   textColor value: ${textColor.toUInt().toString(16)}")
    println("📸   textPaint.color: ${textPaint.color.toUInt().toString(16)}")
    println("📸   Font size: $titleTextSize")
    println("📸   Font configured: true")

    // Left-align the text with padding
    val textX = titlePadding
    val textY = titleHeight / 2f + titleTextSize / 3f  // Center vertically

    println("📸 Text measurements:")
    println("📸   textX (left-aligned with padding): $textX")
    println("📸   textY: $textY")
    println("📸 About to draw text: '$title'")

    // Use TextLine instead of drawString for proper text rendering on iOS
    val textLine = TextLine.make(title, font)
    canvas.drawTextLine(textLine, textX, textY, textPaint)
    println("📸 Text drawn successfully - drawTextLine called")

    // Step 3: Draw original chart bitmap BELOW the title area (so it doesn't cover the title)
    println("📸 Drawing chart at Y offset: $titleHeight")
    val chartImage = org.jetbrains.skia.Image.makeFromBitmap(skiaBitmap)
    canvas.drawImage(chartImage, 0f, titleHeight.toFloat())
    println("📸 Chart drawn successfully")

    // Convert surface to ImageBitmap
    val resultImage = surface.makeImageSnapshot()
    return resultImage.toComposeImageBitmap()
}
