package com.joebad.fastbreak.logging

import platform.Foundation.NSUUID

actual object SentryLogger {
    actual fun addBreadcrumb(category: String, message: String, data: Map<String, Any>?) {
        // Log breadcrumb - iOS Sentry SDK handles crash reporting natively
        // These logs help with debugging
        val dataStr = data?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: ""
        println("📍 [$category] $message ${if (dataStr.isNotEmpty()) "($dataStr)" else ""}")
    }

    actual fun startSpan(operation: String, description: String): String {
        val spanId = NSUUID().UUIDString
        println("⏱️ START [$operation] $description (span: $spanId)")
        return spanId
    }

    actual fun finishSpan(spanId: String, status: SpanStatus) {
        println("⏱️ END (span: $spanId) status: $status")
    }

    actual fun captureException(throwable: Throwable, extras: Map<String, Any>?) {
        val extrasStr = extras?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: ""
        println("🚨 EXCEPTION: ${throwable.message} ${if (extrasStr.isNotEmpty()) "($extrasStr)" else ""}")
        throwable.printStackTrace()
    }

    actual fun captureMessage(message: String, level: SentryLevel) {
        val levelEmoji = when (level) {
            SentryLevel.DEBUG -> "🔍"
            SentryLevel.INFO -> "ℹ️"
            SentryLevel.WARNING -> "⚠️"
            SentryLevel.ERROR -> "❌"
        }
        println("$levelEmoji [$level] $message")
    }
}
