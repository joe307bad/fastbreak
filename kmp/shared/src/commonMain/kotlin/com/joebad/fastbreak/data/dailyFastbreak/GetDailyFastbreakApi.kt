
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
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


suspend fun getDailyFastbreakApi(url: String): DailyFastbreak? {
    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }


    return try {
        client.get(url).body<DailyFastbreak>()
    } catch (e: Exception) {
        println("Error fetching data: ${e.message}")
        null
    } finally {
        client.close()
    }
}
