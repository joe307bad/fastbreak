
import com.joebad.fastbreak.BuildKonfig
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

@Serializable
data class ProfileInitializationResult(
    val baseUrl: String,
    val response: InitializeProfileResponse
)

@Serializable
data class LoginRequest(
    val googleIdToken: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val userId: String?,
    val message: String
)

class ProfileRepository(authRepository: AuthRepository) {

    private val _authRepository = authRepository;
    private val _baseUrl = BuildKonfig.API_BASE_URL
    private val _saveUserName = "${_baseUrl}/profile"
    private val _initializeProfile = "$_baseUrl/profile/initialize"
    private val _authLogin = "$_baseUrl/auth/login"
    private val _authRefresh = "$_baseUrl/auth/refresh"

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
                setBody(Profile(userName = userName, userId = null))
            }.body()
        } catch (e: Exception) {
            println("Error making POST to ${_saveUserName}: ${e.message}")
            null
        }
    }

    suspend fun initializeProfile(googleUser: GoogleUser): ProfileInitializationResult? {
        return try {
            val response = client.post(_initializeProfile) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${googleUser.idToken}")
            }.body<InitializeProfileResponse>()

            ProfileInitializationResult(
                baseUrl = _baseUrl,
                response = response
            )
        } catch (e: Exception) {
            println("Error making POST to ${_initializeProfile}: ${e.message}")
            println("Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            null
        }
    }

    @Deprecated("Use initializeProfile(GoogleUser) instead")
    suspend fun initializeProfile(userId: String, token: String?): ProfileInitializationResult? {
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
            ProfileInitializationResult(
                baseUrl = _baseUrl,
                response = response
            )
        } catch (e: Exception) {
            println("Error making POST to ${_initializeProfile}/${userId}: ${e.message}")
            println("Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            null
        }
    }

    suspend fun login(googleIdToken: String): AuthResponse? {
        return try {
            val response = client.post(_authLogin) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(googleIdToken = googleIdToken))
            }.body<AuthResponse>()

            response
        } catch (e: Exception) {
            println("Error making POST to ${_authLogin}: ${e.message}")
            println("Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            null
        }
    }

    suspend fun refreshToken(refreshToken: String): AuthResponse? {
        return try {
            val response = client.post(_authRefresh) {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken = refreshToken))
            }.body<AuthResponse>()

            response
        } catch (e: Exception) {
            println("Error making POST to ${_authRefresh}: ${e.message}")
            println("Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            null
        }
    }
}