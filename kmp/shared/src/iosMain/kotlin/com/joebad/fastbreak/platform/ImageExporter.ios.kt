package com.joebad.fastbreak.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
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
