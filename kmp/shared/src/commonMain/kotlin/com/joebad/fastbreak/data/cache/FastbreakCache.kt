package com.joebad.fastbreak.data.cache

import com.joebad.fastbreak.data.dailyFastbreak.ScheduleResult
import com.joebad.fastbreak.data.dailyFastbreak.StatsResult
import com.joebad.fastbreak.model.dtos.ScheduleResponse
import com.joebad.fastbreak.model.dtos.StatsResponse
import io.ktor.client.HttpClient
import kotbase.Database

class FastbreakCache private constructor(
    private val ttlClient: TTLCachedHttpClient
) {
    companion object {
        fun create(database: Database, httpClient: HttpClient): FastbreakCache {
            val apiCache = KotbaseApiCache(database)
            val ttlCache = TTLKotbaseApiCache(database)
            val cachedHttpClient = CachedHttpClient(httpClient, apiCache)
            val ttlCachedClient = TTLCachedHttpClient(cachedHttpClient, ttlCache)
            
            return FastbreakCache(ttlCachedClient)
        }
    }
    
    suspend fun getSchedule(url: String): ScheduleResult {
        return try {
            val response = ttlClient.get(
                urlString = url,
                deserializer = ScheduleResponse.serializer(),
                expirationStrategy = ScheduleExpirationStrategy()
            )
            
            when {
                response.isSuccess -> ScheduleResult.Success(
                    response = response.data!!,
                    isFromCache = response.isFromCache,
                    rawJson = response.rawJson,
                    isExpired = response.isExpired,
                    isRefreshing = response.isRefreshing,
                    expiresAt = response.expiresAt
                )
                else -> ScheduleResult.Error(response.error ?: "Unknown error")
            }
        } catch (e: Exception) {
            ScheduleResult.Error("Unexpected error: ${e.message}")
        }
    }
    
    suspend fun getStats(url: String): StatsResult {
        return try {
            val response = ttlClient.get(
                urlString = url,
                deserializer = StatsResponse.serializer(),
                expirationStrategy = StatsExpirationStrategy()
            )
            
            when {
                response.isSuccess -> StatsResult.Success(
                    response = response.data!!,
                    isFromCache = response.isFromCache,
                    rawJson = response.rawJson,
                    isExpired = response.isExpired,
                    isRefreshing = response.isRefreshing,
                    expiresAt = response.expiresAt
                )
                else -> StatsResult.Error(response.error ?: "Unknown error")
            }
        } catch (e: Exception) {
            StatsResult.Error("Unexpected error: ${e.message}")
        }
    }
    
    suspend fun setSchedule(url: String, data: ScheduleResponse) {
        ttlClient.set(
            urlString = url,
            data = data,
            serializer = ScheduleResponse.serializer(),
            expirationStrategy = ScheduleExpirationStrategy()
        )
    }
    
    suspend fun setStats(url: String, data: StatsResponse) {
        ttlClient.set(
            urlString = url,
            data = data,
            serializer = StatsResponse.serializer(),
            expirationStrategy = StatsExpirationStrategy()
        )
    }
}