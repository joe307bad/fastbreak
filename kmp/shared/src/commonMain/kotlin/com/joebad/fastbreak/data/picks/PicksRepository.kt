package com.joebad.fastbreak.data.picks

import AuthRepository
import com.joebad.fastbreak.BuildKonfig
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LockPicksResponse(
    val id: String
)

class PicksRepository(authRepository: AuthRepository) {

    private val _authRepository = authRepository
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

    suspend fun lockPicks(selectionState: FastbreakSelectionState): LockPicksResponse? {
        return try {
            client.post(_lockPicks) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${_authRepository.getUser()?.idToken}")
                setBody(selectionState)
            }.body()
        } catch (e: Exception) {
            println("Error making POST to ${_lockPicks}: ${e.message}")
            null
        }
    }
}