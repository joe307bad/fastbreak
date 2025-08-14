package com.joebad.fastbreak.data.dailyFastbreak

import AuthedUser
import com.joebad.fastbreak.data.cache.CachedHttpClient
import com.joebad.fastbreak.data.cache.CachedTypedResponse
import com.joebad.fastbreak.model.dtos.DailyResponse
import com.joebad.fastbreak.model.dtos.ScheduleResponse
import com.joebad.fastbreak.model.dtos.StatsResponse
import com.joebad.fastbreak.data.cache.TTLCachedHttpClient
import com.joebad.fastbreak.data.cache.ScheduleExpirationStrategy
import com.joebad.fastbreak.data.cache.StatsExpirationStrategy
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
data class LockCardResponse(
    val id: String
)

sealed class LockCardResult {
    data class Success(val response: LockCardResponse) : LockCardResult()
    object AuthenticationRequired : LockCardResult()
    data class Error(val message: String) : LockCardResult()
}

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
        val isRefreshing: Boolean = false
    ) : ScheduleResult()
    data class Error(val message: String) : ScheduleResult()
}

sealed class StatsResult {
    data class Success(
        val response: StatsResponse,
        val isFromCache: Boolean = false,
        val rawJson: String? = null,
        val isExpired: Boolean = false,
        val isRefreshing: Boolean = false
    ) : StatsResult()
    data class Error(val message: String) : StatsResult()
}

val client = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
    }
}

suspend fun getDailyFastbreak(url: String, userId: String? = ""): DailyFastbreakResult {
    return try {
        val httpResponse = client.get {
            url(url)
            parameter("userId", userId)
        }
        
        when (httpResponse.status) {
            HttpStatusCode.OK -> {
                val response = httpResponse.body<DailyResponse>()
                DailyFastbreakResult.Success(response)
            }
            else -> {
                println("Error fetching daily fastbreak: ${httpResponse.status}")
                DailyFastbreakResult.Error("HTTP ${httpResponse.status.value}: ${httpResponse.status.description}")
            }
        }
    } catch (e: ClientRequestException) {
        println("Client error fetching daily fastbreak: ${e.message}")
        DailyFastbreakResult.Error("Network error: ${e.message ?: "Unknown client error"}")
    } catch (e: Exception) {
        println("Unexpected error fetching daily fastbreak: ${e.message}")
        DailyFastbreakResult.Error("Unexpected error: ${e.message ?: "Unknown error occurred"}")
    }
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

suspend fun lockDailyFastbreakCard(
    url: String,
    fastbreakSelectionState: FastbreakSelectionState,
    authedUser: AuthedUser
): LockCardResult {
    return try {
        val httpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${authedUser.idToken}")
            setBody(fastbreakSelectionState)
        }
        
        // Check status before attempting to deserialize body
        when (httpResponse.status) {
            HttpStatusCode.OK -> {
                val response = httpResponse.body<LockCardResponse>()
                LockCardResult.Success(response)
            }
            HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized -> {
                println("Authentication required: ${httpResponse.status}")
                LockCardResult.AuthenticationRequired
            }
            else -> {
                println("Error locking fastbreak card: ${httpResponse.status}")
                LockCardResult.Error("HTTP ${httpResponse.status.value}: ${httpResponse.status.description}")
            }
        }
    } catch (e: ClientRequestException) {
        when (e.response.status) {
            HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized -> {
                println("Authentication required: ${e.message}")
                LockCardResult.AuthenticationRequired
            }
            else -> {
                println("Error locking fastbreak card: ${e.message}")
                LockCardResult.Error(e.message ?: "Unknown error occurred")
            }
        }
    } catch (e: Exception) {
        println("Unexpected error locking fastbreak card: ${e.message}")
        LockCardResult.Error(e.message ?: "Unknown error occurred")
    }
}


suspend fun getScheduleCached(
    ttlCachedClient: TTLCachedHttpClient,
    url: String
): ScheduleResult {
    return try {
        val response = ttlCachedClient.get(
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
                isRefreshing = response.isRefreshing
            )
            else -> ScheduleResult.Error(response.error ?: "Unknown error")
        }
    } catch (e: Exception) {
        ScheduleResult.Error("Unexpected error: ${e.message}")
    }
}

suspend fun getStatsCached(
    ttlCachedClient: TTLCachedHttpClient,
    url: String
): StatsResult {
    return try {
        val response = ttlCachedClient.get(
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
                isRefreshing = response.isRefreshing
            )
            else -> StatsResult.Error(response.error ?: "Unknown error")
        }
    } catch (e: Exception) {
        StatsResult.Error("Unexpected error: ${e.message}")
    }
}
