package com.joebad.fastbreak

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Android-specific coroutine exception handler that logs to LogCat
 */
actual val PlatformCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    val errorMessage = """
        🔥 Android Coroutine Exception:
        Type: ${throwable::class.simpleName}
        Message: ${throwable.message}
    """.trimIndent()
    
    // Log to Android LogCat
    Log.e("FastbreakApp", errorMessage, throwable)
    
    // Also use regular println for consistency
    println(errorMessage)
    throwable.printStackTrace()
}