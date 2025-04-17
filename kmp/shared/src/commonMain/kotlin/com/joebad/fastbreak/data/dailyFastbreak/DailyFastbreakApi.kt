package com.joebad.fastbreak.data.dailyFastbreak

import AuthedUser
import com.joebad.fastbreak.model.dtos.DailyResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
data class LockCardResponse(
    val id: String
)

val client = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

suspend fun getDailyFastbreak(url: String, userId: String? = ""): DailyResponse? {
    return try {
        client.get {
            url(url)
            parameter("userId", userId)
        }.body<DailyResponse>()
    } catch (e: Exception) {
        println("Error fetching data: ${e.message}")
        null
    }
}


suspend fun lockDailyFastbreakCard(
    url: String,
    fastbreakSelectionState: FastbreakSelectionState,
    authedUser: AuthedUser
): LockCardResponse? {
    return try {
        client.post(url) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${authedUser.idToken}")
            setBody(fastbreakSelectionState)
        }.body<LockCardResponse>()
    } catch (e: Exception) {
        println("Error locking fastbreak card: ${e.message}")
        null
    }
}
