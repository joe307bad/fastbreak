
import com.joebad.fastbreak.BuildKonfig
import com.joebad.fastbreak.getPlatform
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
data class Profile(
    val userName: String,
    val userId: String?
)

class ProfileRepository(authRepository: AuthRepository) {

    private val _authRepository = authRepository;
    private val _baseUrl = if (getPlatform().name == "iOS") "localhost" else BuildKonfig.API_BASE_URL
    private val _saveUserName = "${_baseUrl}/api/profile"
    private val _initializeProfile = "$_baseUrl/api/profile/initialize"

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

    suspend fun saveUserName(userName: String): Unit? {
        return try {
            _authRepository.updateUserName(userName);
            client.post(_saveUserName) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${_authRepository.getUser()?.idToken}")
                setBody(Profile(userName = userName, userId = _authRepository.getUser()?.userId))
            }.body()
        } catch (e: Exception) {
            println("Error making POST to ${_saveUserName}: ${e.message}")
            null
        }
    }

    suspend fun initializeProfile(userId: String, token: String?): InitializeProfileResponse? {
        return try {
            val t = token ?: _authRepository.getUser()?.idToken;
            println("Initializing profile for userId: $userId")
            println("API endpoint: ${_initializeProfile}/${userId}")
            println("Token present: ${t != null}")
            val response = client.post("${_initializeProfile}/${userId}") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $t")
            }.body<InitializeProfileResponse>()
            println("Profile initialization successful: $response")
            response
        } catch (e: Exception) {
            println("Error making POST to ${_initializeProfile}/${userId}: ${e.message}")
            println("Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            null
        }
    }
}