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
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.staticCFunction

class IOSImageExporter : ImageExporter {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun shareImage(bitmap: ImageBitmap, title: String) {
        try {
            // Convert ImageBitmap to Skia Bitmap
            val skiaBitmap = bitmap.asSkiaBitmap()

            // Scale down if image is too large for Twitter (max 4096x4096)
            val maxDimension = 4096
            val scaledBitmap = if (skiaBitmap.width > maxDimension || skiaBitmap.height > maxDimension) {
                val scale = minOf(
                    maxDimension.toFloat() / skiaBitmap.width,
                    maxDimension.toFloat() / skiaBitmap.height
                )
                val newWidth = (skiaBitmap.width * scale).toInt()
                val newHeight = (skiaBitmap.height * scale).toInt()

                println("📸 Scaling image from ${skiaBitmap.width}x${skiaBitmap.height} to ${newWidth}x${newHeight}")

                val surface = Surface.makeRasterN32Premul(newWidth, newHeight)
                val canvas = surface.canvas
                val image = org.jetbrains.skia.Image.makeFromBitmap(skiaBitmap)
                canvas.drawImageRect(
                    image,
                    org.jetbrains.skia.Rect.makeWH(skiaBitmap.width.toFloat(), skiaBitmap.height.toFloat()),
                    org.jetbrains.skia.Rect.makeWH(newWidth.toFloat(), newHeight.toFloat()),
                    org.jetbrains.skia.SamplingMode.DEFAULT,
                    null,
                    true
                )
                surface.makeImageSnapshot().toComposeImageBitmap().asSkiaBitmap()
            } else {
                skiaBitmap
            }

            // Encode to PNG for better compatibility
            val encodedData = org.jetbrains.skia.Image.makeFromBitmap(scaledBitmap)
                .encodeToData(EncodedImageFormat.PNG)
                ?: throw IllegalStateException("Failed to encode bitmap to PNG")

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

            // Get the root view controller and present the share sheet on the main thread
            // This is critical for Twitter and other social media apps to work correctly
            dispatch_async(dispatch_get_main_queue()) {
                val keyWindow = UIApplication.sharedApplication.keyWindow
                val rootViewController = keyWindow?.rootViewController

                if (rootViewController != null) {
                    rootViewController.presentViewController(
                        viewControllerToPresent = activityViewController,
                        animated = true,
                        completion = null
                    )
                } else {
                    println("Error: No root view controller available")
                }
            }

        } catch (e: Exception) {
            println("Error sharing image: ${e.message}")
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

    // Start with a base text size and scale down if needed to fit (ensure single line)
    var titleTextSize = 48f
    val typeface = org.jetbrains.skia.FontMgr.default.matchFamilyStyle("Courier", org.jetbrains.skia.FontStyle.NORMAL)
    var font = Font(typeface, titleTextSize)

    // Measure text and scale down if it doesn't fit (ensure single line, no wrapping)
    var textLine = TextLine.make(title, font)
    while (textLine.width > maxTextWidth && titleTextSize > 12f) {
        titleTextSize -= 1f
        font = Font(typeface, titleTextSize)
        textLine = TextLine.make(title, font)
    }

    val titleHeight = (titleTextSize + titlePadding * 2).toInt()

    // Footer dimensions
    val footerPadding = 16f
    val footerTextSize = 30f
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
