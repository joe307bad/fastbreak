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
