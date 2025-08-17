package com.joebad.fastbreak.data.dailyFastbreak

import com.joebad.fastbreak.data.cache.CachedHttpClient
import com.joebad.fastbreak.data.cache.CachedTypedResponse
import com.joebad.fastbreak.model.dtos.DailyResponse
import com.joebad.fastbreak.model.dtos.ScheduleResponse
import com.joebad.fastbreak.model.dtos.StatsResponse
import kotlinx.datetime.Instant


sealed class DailyFastbreakResult {
    data class Success(
        val response: DailyResponse,
        val isFromCache: Boolean = false,
        val rawJson: String? = null
    ) : DailyFastbreakResult()
    data class Error(val message: String) : DailyFastbreakResult()
}

sealed class ScheduleResult {
    data class Success(
        val response: ScheduleResponse,
        val isFromCache: Boolean = false,
        val rawJson: String? = null,
        val isExpired: Boolean = false,
        val isRefreshing: Boolean = false,
        val expiresAt: Instant? = null,
        val cachedAt: Instant? = null
    ) : ScheduleResult()
    data class Error(val message: String) : ScheduleResult()
}

sealed class StatsResult {
    data class Success(
        val response: StatsResponse,
        val isFromCache: Boolean = false,
        val rawJson: String? = null,
        val isExpired: Boolean = false,
        val isRefreshing: Boolean = false,
        val expiresAt: Instant? = null,
        val cachedAt: Instant? = null
    ) : StatsResult()
    data class Error(val message: String) : StatsResult()
}

@Deprecated("Use FastbreakCache.getSchedule() or FastbreakCache.getStats() instead")
suspend fun getDailyFastbreakCached(
    cachedHttpClient: CachedHttpClient,
    url: String, 
    userId: String? = ""
): DailyFastbreakResult {
    return try {
        val fullUrl = if (userId.isNullOrEmpty()) url else "$url?userId=$userId"
        val cachedResponse: CachedTypedResponse<DailyResponse> = cachedHttpClient.getTyped(
            urlString = fullUrl,
            deserializer = DailyResponse.serializer()
        )
        
        when {
            cachedResponse.isSuccess -> {
                DailyFastbreakResult.Success(
                    response = cachedResponse.data!!,
                    isFromCache = cachedResponse.isFromCache,
                    rawJson = cachedResponse.rawJson
                )
            }
            else -> {
                val errorMessage = cachedResponse.error ?: "Unknown error occurred"
                println("Error fetching cached daily fastbreak: $errorMessage")
                DailyFastbreakResult.Error(errorMessage)
            }
        }
    } catch (e: Exception) {
        println("Unexpected error fetching cached daily fastbreak: ${e.message}")
        DailyFastbreakResult.Error("Unexpected error: ${e.message ?: "Unknown error occurred"}")
    }
}