package com.joebad.fastbreak.ui.diagnostics

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Formats an Instant timestamp as a relative time string.
 * Examples: "now", "2m", "5h", "3d", "2w"
 *
 * @param instant The timestamp to format
 * @return Compact relative time string
 */
fun formatTimeAgo(instant: Instant): String {
    val now = Clock.System.now()
    val duration = now - instant

    return when {
        duration < 1.minutes -> "now"
        duration < 1.hours -> "${duration.inWholeMinutes}m"
        duration < 24.hours -> "${duration.inWholeHours}h"
        duration < 7.days -> "${duration.inWholeDays}d"
        else -> "${duration.inWholeDays / 7}w"
    }
}

/**
 * Formats a byte count as a human-readable size string.
 * Examples: "1024B", "2KB", "1MB"
 *
 * @param bytes The number of bytes
 * @return Formatted size string
 */
fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${bytes / (1024 * 1024)}MB"
    }
}
