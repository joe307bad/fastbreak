package com.joebad.fastbreak.platform

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream

class AndroidImageExporter(private val context: Context) : ImageExporter {
    override suspend fun shareImage(bitmap: ImageBitmap, title: String) {
        try {
            println("📤 shareImage - Received bitmap to share")
            println("📤 Bitmap dimensions: ${bitmap.width}x${bitmap.height}")

            // Convert ImageBitmap to Android Bitmap
            val sourceBitmap = bitmap.asAndroidBitmap()
            // Copy to software bitmap if it's a hardware bitmap (hardware bitmaps can't be compressed)
            val androidBitmap = if (sourceBitmap.config == Bitmap.Config.HARDWARE) {
                sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                sourceBitmap
            }
            println("📤 Converted to Android bitmap: ${androidBitmap.width}x${androidBitmap.height}")

            // Save to MediaStore Downloads directory - works better with Files app
            val filename = "fastbreak_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Fastbreak")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val imageUri = resolver.insert(collection, contentValues)
                ?: throw Exception("Failed to create MediaStore entry")

            println("📤 Saving to MediaStore Downloads: $imageUri")

            // Write bitmap to MediaStore
            resolver.openOutputStream(imageUri)?.use { out ->
                androidBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            } ?: throw Exception("Failed to open output stream")

            // Mark as no longer pending (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            println("📤 File saved successfully to MediaStore Downloads")

            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Start share activity
            val chooserIntent = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
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

actual fun addTitleToBitmap(bitmap: ImageBitmap, title: String, isDarkTheme: Boolean, textColor: Int, source: String): ImageBitmap {
    val sourceBitmap = bitmap.asAndroidBitmap()
    // Copy to software bitmap if it's a hardware bitmap (hardware bitmaps can't be used with Canvas)
    val androidBitmap = if (sourceBitmap.config == Bitmap.Config.HARDWARE) {
        sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        sourceBitmap
    }

    val titlePadding = 32f
    val footerPadding = 16f
    val maxTextWidth = androidBitmap.width - (titlePadding * 2)

    // Start with a base text size and scale down if needed to fit
    var titleTextSize = 48f
    val textPaint = Paint().apply {
        color = textColor
        textSize = titleTextSize
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    // Measure text and scale down if it doesn't fit
    var textWidth = textPaint.measureText(title)
    while (textWidth > maxTextWidth && titleTextSize > 16f) {
        titleTextSize -= 2f
        textPaint.textSize = titleTextSize
        textWidth = textPaint.measureText(title)
    }

    val titleHeight = (titleTextSize + titlePadding * 2).toInt()

    // Footer dimensions
    val footerTextSize = 36f
    val footerHeight = (footerTextSize + footerPadding * 2).toInt()

    println("📸 addTitleToBitmap - title: '$title', isDarkTheme: $isDarkTheme")
    println("📸 Original bitmap size: ${androidBitmap.width}x${androidBitmap.height}")
    println("📸 New bitmap size: ${androidBitmap.width}x${androidBitmap.height + titleHeight + footerHeight}")
    println("📸 Title height: $titleHeight, Footer height: $footerHeight")

    // Create a new bitmap with extra height for title and footer
    val newBitmap = Bitmap.createBitmap(
        androidBitmap.width,
        androidBitmap.height + titleHeight + footerHeight,
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

    // Step 2: Draw title text
    val textY = titleHeight / 2f + titleTextSize / 3f
    val textX = titlePadding
    canvas.drawText(title, textX, textY, textPaint)
    println("📸 Title drawn successfully")

    // Step 3: Draw original chart bitmap BELOW the title area
    canvas.drawBitmap(androidBitmap, 0f, titleHeight.toFloat(), null)
    println("📸 Chart drawn successfully")

    // Step 4: Draw footer with source (left) and fbrk.app (right)
    val footerPaint = Paint().apply {
        color = textColor
        textSize = footerTextSize
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        isAntiAlias = true
    }

    val footerY = titleHeight + androidBitmap.height + footerHeight / 2f + footerTextSize / 3f

    // Draw source on the left
    if (source.isNotEmpty()) {
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("source: $source", footerPadding, footerY, footerPaint)
    }

    // Draw fbrk.app on the right
    footerPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText("fbrk.app", newBitmap.width - footerPadding, footerY, footerPaint)
    println("📸 Footer drawn successfully")

    println("📸 FINAL bitmap size: ${newBitmap.width}x${newBitmap.height}")
    println("📸 addTitleToBitmap COMPLETE - returning bitmap with title and footer")

    return newBitmap.asImageBitmap()
}
