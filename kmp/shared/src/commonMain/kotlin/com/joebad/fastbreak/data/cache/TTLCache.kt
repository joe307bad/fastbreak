package com.joebad.fastbreak.data.cache

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CacheMetadata(
    val cachedAt: Instant,
    val expiresAt: Instant
)

data class TTLCachedResponse<T>(
    val data: T?,
    val isSuccess: Boolean,
    val isFromCache: Boolean,
    val rawJson: String?,
    val error: String?,
    val cachedAt: Instant?,
    val expiresAt: Instant?,
    val isExpired: Boolean = false,
    val isRefreshing: Boolean = false
)

interface CacheExpirationStrategy {
    fun calculateExpirationTime(currentTime: Instant): Instant
}