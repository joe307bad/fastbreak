import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import com.joebad.fastbreak.getPlatform
import com.liftric.kvault.KVault
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AuthedUser(
    val email: String,
    val exp: Long,
    val idToken: String,
    val userId: String,
    val userName: String
)

@Serializable
data class UserApiResponse(
    val userName: String? = null,
    val lockedFastBreakCard: FastbreakSelectionState? = null
)

class AuthRepository(private val secureStorage: KVault) {

    companion object {
        private const val KEY_AUTHED_USER = "authed_user"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = if (getPlatform().name == "iOS") "localhost" else "10.0.2.2"

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

    suspend fun storeUser(user: AuthedUser): FastbreakSelectionState? {
        try {
            val response: UserApiResponse = client.get("http://$baseUrl:8085/api/profile/${user.userId}") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${user.idToken}")
            }.body()

            val userJson = json.encodeToString(
                AuthedUser(
                    user.email,
                    user.exp,
                    user.idToken,
                    user.userId,
                    response.userName ?: ""
                )
            )
            secureStorage.set(KEY_AUTHED_USER, userJson)
            return response.lockedFastBreakCard
        } catch (e: Exception) {
            println("Error fetching user data: ${e.message}")
            throw e
        }
    }

    fun getUser(): AuthedUser? {
        val userJson = secureStorage.string(KEY_AUTHED_USER) ?: return null
        return try {
            json.decodeFromString(userJson)
        } catch (e: Exception) {
            null
        }
    }

    fun isUserExpired(user: AuthedUser?): Boolean {
        if (user == null) return true

        val currentTimeSeconds = Clock.System.now().epochSeconds
        return user.exp < currentTimeSeconds
    }

    fun clearUser() {
        secureStorage.deleteObject(KEY_AUTHED_USER)
    }
}