import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
data class LeaderboardItem(val id: String, val user: String, val points: Int)


@Serializable
data class EmptyFastbreakCardItem(
    val id: String,
    val type: String,
    val homeTeam: String? = null,
    val homeTeamSubtitle: String? = null,
    val awayTeam: String? = null,
    val awayTeamSubtitle: String? = null,
    val dateLine1: String? = null,
    val dateLine2: String? = null,
    val dateLine3: String? = null,
    val points: Int,
    val question: String? = null,
    val answer1: String? = null,
    val answer2: String? = null,
    val answer3: String? = null,
    val answer4: String? = null,
    val correctAnswer: String? = null
)


@Serializable
data class DailyFastbreak(
    val leaderboard: List<LeaderboardItem>,
    val fastbreakCard: List<EmptyFastbreakCardItem>
)

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

suspend fun getDailyFastbreak(url: String): DailyFastbreak? {
    return try {
        client.get(url).body<DailyFastbreak>()
    } catch (e: Exception) {
        println("Error fetching data: ${e.message}")
        null
    } finally {
        client.close()
    }
}


suspend fun lockDailyFastbreakCard(
    url: String,
    fastbreakSelectionState: FastbreakSelectionState
): LockCardResponse? {
    return try {
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(fastbreakSelectionState)
        }.body<LockCardResponse>()
    } catch (e: Exception) {
        println("Error locking fastbreak card: ${e.message}")
        null
    }
}
