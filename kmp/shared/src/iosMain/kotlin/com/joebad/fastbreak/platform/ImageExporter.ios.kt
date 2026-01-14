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

actual fun addTitleToBitmap(bitmap: ImageBitmap, title: String, isDarkTheme: Boolean, textColor: Int, source: String): ImageBitmap {
    val skiaBitmap = bitmap.asSkiaBitmap()

    // Calculate title dimensions
    val titlePadding = 32f
    val maxTextWidth = skiaBitmap.width - (titlePadding * 2)

    // Start with a base text size and scale down if needed to fit
    var titleTextSize = 48f
    val typeface = org.jetbrains.skia.FontMgr.default.matchFamilyStyle("Courier", org.jetbrains.skia.FontStyle.NORMAL)
    var font = Font(typeface, titleTextSize)

    // Measure text and scale down if it doesn't fit
    var textLine = TextLine.make(title, font)
    while (textLine.width > maxTextWidth && titleTextSize > 16f) {
        titleTextSize -= 2f
        font = Font(typeface, titleTextSize)
        textLine = TextLine.make(title, font)
    }

    val titleHeight = (titleTextSize + titlePadding * 2).toInt()

    // Footer dimensions
    val footerPadding = 16f
    val footerTextSize = 36f
    val footerHeight = (footerTextSize + footerPadding * 2).toInt()

    println("📸 ========================================")
    println("📸 addTitleToBitmap - title: '$title', isDarkTheme: $isDarkTheme")
    println("📸 Original bitmap size: ${skiaBitmap.width}x${skiaBitmap.height}")
    println("📸 Title height: $titleHeight, Footer height: $footerHeight")
    println("📸 ========================================")

    // Create a new surface with extra height for title and footer
    val newWidth = skiaBitmap.width
    val newHeight = skiaBitmap.height + titleHeight + footerHeight
    val surface = Surface.makeRasterN32Premul(newWidth, newHeight)
    val canvas = surface.canvas

    // Use theme colors: dark background with white text, or white background with black text
    val backgroundColor = if (isDarkTheme) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    println("📸 Background color: ${backgroundColor.toUInt().toString(16)}, Text color: ${textColor.toUInt().toString(16)}")

    // Step 1: Draw full background first
    val backgroundPaint = Paint().apply {
        color = backgroundColor
    }
    canvas.drawRect(
        org.jetbrains.skia.Rect.makeXYWH(0f, 0f, newWidth.toFloat(), newHeight.toFloat()),
        backgroundPaint
    )
    println("📸 Background drawn")

    // Step 2: Draw title text
    val textPaint = Paint()
    textPaint.color = textColor
    textPaint.isAntiAlias = true

    val textX = titlePadding
    val textY = titleHeight / 2f + titleTextSize / 3f

    canvas.drawTextLine(textLine, textX, textY, textPaint)
    println("📸 Title drawn successfully")

    // Step 3: Draw original chart bitmap BELOW the title area
    val chartImage = org.jetbrains.skia.Image.makeFromBitmap(skiaBitmap)
    canvas.drawImage(chartImage, 0f, titleHeight.toFloat())
    println("📸 Chart drawn successfully")

    // Step 4: Draw footer with source (left) and fbrk.app (right)
    val footerFont = Font(typeface, footerTextSize)
    val footerY = titleHeight + skiaBitmap.height + footerHeight / 2f + footerTextSize / 3f

    // Draw source on the left
    if (source.isNotEmpty()) {
        val sourceText = TextLine.make("source: $source", footerFont)
        canvas.drawTextLine(sourceText, footerPadding, footerY, textPaint)
    }

    // Draw fbrk.app on the right
    val brandText = TextLine.make("fbrk.app", footerFont)
    val brandX = newWidth - footerPadding - brandText.width
    canvas.drawTextLine(brandText, brandX, footerY, textPaint)
    println("📸 Footer drawn successfully")

    // Convert surface to ImageBitmap
    val resultImage = surface.makeImageSnapshot()
    return resultImage.toComposeImageBitmap()
}
