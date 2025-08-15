package com.joebad.fastbreak.data.picks

import AuthRepository
import com.joebad.fastbreak.BuildKonfig
import com.joebad.fastbreak.data.auth.GoogleAuthService
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LockPicksResponse(
    val id: String
)

sealed class LockPicksResult {
    data class Success(val response: LockPicksResponse) : LockPicksResult()
    object TokenRefreshRequired : LockPicksResult()
    data class Error(val message: String) : LockPicksResult()
}

class PicksRepository(authRepository: AuthRepository) {

    private val _authRepository = authRepository
    private val _googleAuthService = GoogleAuthService(authRepository)
    private val _baseUrl = BuildKonfig.API_BASE_URL
    private val _lockPicks = "${_baseUrl}/lock"

    private val client = HttpClient {
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

    suspend fun lockPicks(selectionState: FastbreakSelectionState): LockPicksResult {
        return try {
            val response = makeAuthenticatedRequest(selectionState)
            when {
                response.status == HttpStatusCode.Unauthorized -> {
                    // Token expired, signal that refresh is needed
                    println("Token expired, signaling refresh required...")
                    LockPicksResult.TokenRefreshRequired
                }
                response.status.value in 200..299 -> {
                    val lockResponse: LockPicksResponse = response.body()
                    LockPicksResult.Success(lockResponse)
                }
                else -> {
                    println("Request failed with status: ${response.status}")
                    LockPicksResult.Error("Request failed with status: ${response.status}")
                }
            }
        } catch (e: Exception) {
            println("Error making POST to ${_lockPicks}: ${e.message}")
            LockPicksResult.Error("Network error: ${e.message}")
        }
    }
    
    suspend fun lockPicksWithRefreshedToken(selectionState: FastbreakSelectionState): LockPicksResult {
        return try {
            // First attempt to refresh token
            val refreshSuccess = _googleAuthService.refreshToken()
            
            if (refreshSuccess) {
                // Retry with potentially new token
                println("Token refreshed successfully, retrying request...")
                val response = makeAuthenticatedRequest(selectionState)
                when {
                    response.status.value in 200..299 -> {
                        val lockResponse: LockPicksResponse = response.body()
                        LockPicksResult.Success(lockResponse)
                    }
                    else -> {
                        println("Retry failed with status: ${response.status}")
                        LockPicksResult.Error("Retry failed with status: ${response.status}")
                    }
                }
            } else {
                println("Token refresh failed")
                LockPicksResult.Error("Token refresh failed")
            }
        } catch (e: Exception) {
            println("Error during token refresh and retry: ${e.message}")
            LockPicksResult.Error("Token refresh error: ${e.message}")
        }
    }
    
    private suspend fun makeAuthenticatedRequest(selectionState: FastbreakSelectionState): HttpResponse {
        return client.post(_lockPicks) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${_authRepository.getUser()?.idToken}")
            setBody(selectionState)
        }
    }
}