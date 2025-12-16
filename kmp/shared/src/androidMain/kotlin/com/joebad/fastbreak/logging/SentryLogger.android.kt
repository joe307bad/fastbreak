package com.joebad.fastbreak.logging

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel as AndroidSentryLevel
import io.sentry.SpanStatus as AndroidSpanStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

actual object SentryLogger {
    private val activeSpans = ConcurrentHashMap<String, io.sentry.ISpan>()

    actual fun addBreadcrumb(category: String, message: String, data: Map<String, Any>?) {
        val breadcrumb = Breadcrumb().apply {
            this.category = category
            this.message = message
            this.level = AndroidSentryLevel.INFO
            data?.forEach { (key, value) ->
                this.setData(key, value)
            }
        }
        Sentry.addBreadcrumb(breadcrumb)
    }

    actual fun startSpan(operation: String, description: String): String {
        val spanId = UUID.randomUUID().toString()
        val transaction = Sentry.getSpan()
        val span = transaction?.startChild(operation, description)
        if (span != null) {
            activeSpans[spanId] = span
        }
        return spanId
    }

    actual fun finishSpan(spanId: String, status: SpanStatus) {
        val span = activeSpans.remove(spanId)
        span?.finish(
            when (status) {
                SpanStatus.OK -> AndroidSpanStatus.OK
                SpanStatus.ERROR -> AndroidSpanStatus.INTERNAL_ERROR
                SpanStatus.CANCELLED -> AndroidSpanStatus.CANCELLED
            }
        )
    }

    actual fun captureException(throwable: Throwable, extras: Map<String, Any>?) {
        Sentry.captureException(throwable) { scope ->
            extras?.forEach { (key, value) ->
                scope.setExtra(key, value.toString())
            }
        }
    }

    actual fun captureMessage(message: String, level: SentryLevel) {
        Sentry.captureMessage(message, when (level) {
            SentryLevel.DEBUG -> AndroidSentryLevel.DEBUG
            SentryLevel.INFO -> AndroidSentryLevel.INFO
            SentryLevel.WARNING -> AndroidSentryLevel.WARNING
            SentryLevel.ERROR -> AndroidSentryLevel.ERROR
        })
    }
}
