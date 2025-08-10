package com.joebad.fastbreak.coroutines

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Global coroutine exception handler for debugging uncaught exceptions
 * Prevents crashes by logging exceptions instead of propagating them to the system
 */
val GlobalCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    println("🔥 Uncaught coroutine exception: ${throwable.message}")
    println("🔥 Exception type: ${throwable::class.simpleName}")
    throwable.printStackTrace()
    
    // In a production app, you might want to log this to a crash reporting service
    // CrashReporter.recordException(throwable)
}

/**
 * Safe CoroutineScope with global exception handling for the application
 */
val SafeCoroutineScope = CoroutineScope(
    SupervisorJob() + Dispatchers.Main + GlobalCoroutineExceptionHandler
)

/**
 * Extension function to launch coroutines with automatic exception handling
 */
fun CoroutineScope.safeLaunch(
    block: suspend CoroutineScope.() -> Unit
) {
    launch(GlobalCoroutineExceptionHandler) {
        try {
            block()
        } catch (e: Exception) {
            println("🔥 Exception caught in safeLaunch: ${e.message}")
            e.printStackTrace()
        }
    }
}