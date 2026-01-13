package com.joebad.fastbreak.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class AndroidImageExporter(private val context: Context) : ImageExporter {
    override suspend fun shareImage(bitmap: ImageBitmap, title: String) {
        try {
            // Convert ImageBitmap to Android Bitmap
            val androidBitmap = bitmap.asAndroidBitmap()

            // Create a temporary file in cache directory
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()

            val file = File(cachePath, "scatter_plot_${System.currentTimeMillis()}.jpg")

            // Write bitmap to file as JPEG (no transparency, ensures opaque white background)
            FileOutputStream(file).use { out ->
                androidBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // Get content URI using FileProvider
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Start share activity
            val chooserIntent = Intent.createChooser(shareIntent, title)
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}

// Global context holder
private var applicationContext: Context? = null

fun initializeImageExporter(context: Context) {
    applicationContext = context.applicationContext
}

actual fun getImageExporter(): ImageExporter {
    val context = applicationContext
        ?: throw IllegalStateException("ImageExporter not initialized. Call initializeImageExporter() first.")
    return AndroidImageExporter(context)
}
