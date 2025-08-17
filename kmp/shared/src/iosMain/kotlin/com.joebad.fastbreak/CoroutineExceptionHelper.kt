package com.joebad.fastbreak

import com.joebad.fastbreak.coroutines.GlobalCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSLog

/**
 * iOS-specific coroutine exception handler that logs to NSLog
 * This ensures exceptions are visible in Xcode console and crash reports
 */
actual val PlatformCoroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
    val errorMessage = """
        🔥 iOS Coroutine Exception:
        Type: ${throwable::class.simpleName}
        Message: ${throwable.message}
        Stack trace: ${throwable.stackTraceToString()}
    """.trimIndent()
    
    // Log to NSLog so it appears in Xcode console
    NSLog("FastbreakApp: %s", errorMessage)
    
    // Also use regular println for debug builds
    println(errorMessage)
}

/**
 * iOS-safe coroutine scope with enhanced logging
 */
val IOSSafeCoroutineScope = CoroutineScope(
    SupervisorJob() + 
    Dispatchers.Main + 
    PlatformCoroutineExceptionHandler
)