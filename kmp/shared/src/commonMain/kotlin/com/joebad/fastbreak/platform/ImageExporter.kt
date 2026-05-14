package com.joebad.fastbreak.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform-specific interface for capturing and sharing composable content as images
 */
interface ImageExporter {
    /**
     * Share an image bitmap with the platform's native share dialog
     * @param bitmap The image to share
     * @param title The title/subject for the share dialog
     */
    suspend fun shareImage(bitmap: ImageBitmap, title: String)
}

/**
 * Expect function to get platform-specific ImageExporter implementation
 */
expect fun getImageExporter(): ImageExporter

/**
 * Add a title text to the top and footer to the bottom of a bitmap programmatically
 * @param bitmap The original image bitmap
 * @param title The title text to add at the top
 * @param backgroundColor The background color (ARGB Int format) - used for title/footer area
 * @param textColor The color to use for the text (ARGB Int format)
 * @param source The data source attribution to show in the bottom left
 * @return A new bitmap with the title and footer added
 */
expect fun addTitleToBitmap(bitmap: ImageBitmap, title: String, backgroundColor: Int, textColor: Int, source: String): ImageBitmap

/**
 * Overload without source parameter for backward compatibility
 */
fun addTitleToBitmap(bitmap: ImageBitmap, title: String, backgroundColor: Int, textColor: Int): ImageBitmap =
    addTitleToBitmap(bitmap, title, backgroundColor, textColor, "")
