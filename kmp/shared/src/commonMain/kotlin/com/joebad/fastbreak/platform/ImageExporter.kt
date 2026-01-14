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
 * Add a title text to the top of a bitmap programmatically
 * @param bitmap The original image bitmap
 * @param title The title text to add
 * @param isDarkTheme Whether the app is in dark theme mode
 * @param textColor The color to use for the title text (ARGB Int format)
 * @return A new bitmap with the title added at the top
 */
expect fun addTitleToBitmap(bitmap: ImageBitmap, title: String, isDarkTheme: Boolean, textColor: Int): ImageBitmap
