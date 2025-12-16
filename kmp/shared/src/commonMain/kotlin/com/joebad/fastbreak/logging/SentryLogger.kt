package com.joebad.fastbreak.logging

/**
 * Platform-agnostic Sentry logging interface.
 * Provides breadcrumbs and performance tracking across platforms.
 */
expect object SentryLogger {
    /**
     * Adds a breadcrumb for tracking user/app actions.
     */
    fun addBreadcrumb(category: String, message: String, data: Map<String, Any>? = null)

    /**
     * Starts a performance span for timing operations.
     * Returns a span ID that should be passed to finishSpan.
     */
    fun startSpan(operation: String, description: String): String

    /**
     * Finishes a performance span started with startSpan.
     */
    fun finishSpan(spanId: String, status: SpanStatus = SpanStatus.OK)

    /**
     * Captures an exception to Sentry.
     */
    fun captureException(throwable: Throwable, extras: Map<String, Any>? = null)

    /**
     * Captures a message to Sentry.
     */
    fun captureMessage(message: String, level: SentryLevel = SentryLevel.INFO)
}

enum class SpanStatus {
    OK,
    ERROR,
    CANCELLED
}

enum class SentryLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}
