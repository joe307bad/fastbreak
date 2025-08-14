
import com.liftric.kvault.KVault
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AuthedUser(
    val email: String,
    val exp: Long,
    val idToken: String,
    val userId: String,
//    val userName: String
)

@Serializable
data class GoogleUser(
    val email: String,
    val exp: Long,
    val idToken: String,
//    val userName: String
)

class AuthRepository(private val secureStorage: KVault) {

    companion object {
        private const val KEY_AUTHED_USER = "authed_user"
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun updateUserName(userName: String) {
        val user = getUser() ?: return
        storeAuthedUser(AuthedUser(
            user.email,
            user.exp,
            user.idToken,
            user.userId,
//            userName
        ))
    }

    fun storeAuthedUser(user: AuthedUser) {
        val userJson = json.encodeToString(AuthedUser.serializer(), user)
        secureStorage.set(KEY_AUTHED_USER, userJson)
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