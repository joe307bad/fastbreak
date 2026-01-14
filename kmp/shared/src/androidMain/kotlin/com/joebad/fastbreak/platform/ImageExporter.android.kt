package com.joebad.fastbreak.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class AndroidImageExporter(private val context: Context) : ImageExporter {
    override suspend fun shareImage(bitmap: ImageBitmap, title: String) {
        try {
            println("📤 shareImage - Received bitmap to share")
            println("📤 Bitmap dimensions: ${bitmap.width}x${bitmap.height}")

            // Convert ImageBitmap to Android Bitmap
            val androidBitmap = bitmap.asAndroidBitmap()
            println("📤 Converted to Android bitmap: ${androidBitmap.width}x${androidBitmap.height}")

            // Create a temporary file in cache directory
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()

            val file = File(cachePath, "scatter_plot_${System.currentTimeMillis()}.jpg")
            println("📤 Saving to file: ${file.absolutePath}")

            // Write bitmap to file as JPEG (no transparency, ensures opaque white background)
            FileOutputStream(file).use { out ->
                androidBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            println("📤 File saved successfully, size: ${file.length()} bytes")

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

actual fun addTitleToBitmap(bitmap: ImageBitmap, title: String, isDarkTheme: Boolean, textColor: Int): ImageBitmap {
    val androidBitmap = bitmap.asAndroidBitmap()

    // Calculate title height based on text size - make it bigger and more visible
    val titleTextSize = 64f  // Increased from 48f
    val titlePadding = 48f   // Increased from 32f
    val titleHeight = (titleTextSize + titlePadding * 2).toInt()

    println("📸 addTitleToBitmap - title: '$title', isDarkTheme: $isDarkTheme")
    println("📸 Original bitmap size: ${androidBitmap.width}x${androidBitmap.height}")
    println("📸 New bitmap size: ${androidBitmap.width}x${androidBitmap.height + titleHeight}")
    println("📸 Title height: $titleHeight, text size: $titleTextSize")

    // Create a new bitmap with extra height for title
    val newBitmap = Bitmap.createBitmap(
        androidBitmap.width,
        androidBitmap.height + titleHeight,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(newBitmap)

    // Use theme colors: dark background with white text, or white background with black text
    val backgroundColor = if (isDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE

    println("📸 Background color: $backgroundColor, Text color: $textColor")

    // Step 1: Draw full background first
    val backgroundPaint = Paint().apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, newBitmap.width.toFloat(), newBitmap.height.toFloat(), backgroundPaint)
    println("📸 Background drawn")

    // Step 2: Draw title text in the title area BEFORE drawing the chart
    val textPaint = Paint().apply {
        color = textColor
        textSize = titleTextSize
        // Use Courier (typewriter font) - normal weight, not bold
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    // Position text with left alignment and padding
    val textY = titleHeight / 2f + titleTextSize / 3f
    val textX = titlePadding

    println("📸 Calculating text position:")
    println("📸   textX (left-aligned with padding): $textX")
    println("📸   textY: $textY")
    println("📸   Paint.textAlign = LEFT")
    println("📸 Drawing text FIRST at: ($textX, $textY) in title area")
    canvas.drawText(title, textX, textY, textPaint)
    println("📸 Text drawn successfully")

    // Step 3: Draw original chart bitmap BELOW the title area (so it doesn't cover the title)
    println("📸 Drawing chart at Y offset: $titleHeight")
    canvas.drawBitmap(androidBitmap, 0f, titleHeight.toFloat(), null)
    println("📸 Chart drawn successfully")

    println("📸 FINAL bitmap size: ${newBitmap.width}x${newBitmap.height}")
    println("📸 addTitleToBitmap COMPLETE - returning bitmap with title")

    return newBitmap.asImageBitmap()
}
