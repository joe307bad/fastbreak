package com.joebad.fastbreak.data.dailyFastbreak

import AuthedUser
import com.joebad.fastbreak.model.dtos.DailyResponse
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
    data class Success(val response: DailyResponse) : DailyFastbreakResult()
    data class Error(val message: String) : DailyFastbreakResult()
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
