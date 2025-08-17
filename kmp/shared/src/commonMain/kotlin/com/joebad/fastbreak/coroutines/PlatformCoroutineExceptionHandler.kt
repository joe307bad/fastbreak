package com.joebad.fastbreak

import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Platform-specific coroutine exception handler
 * iOS: Logs to NSLog and Xcode console
 * Android: Logs to standard output
 */
expect val PlatformCoroutineExceptionHandler: CoroutineExceptionHandler