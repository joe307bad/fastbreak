
import com.liftric.kvault.KVault
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AuthedUser(
    val email: String,
    val exp: Long,
    val idToken: String,
    val userId: String
)

class AuthRepository(private val secureStorage: KVault) {

    companion object {
        private const val KEY_AUTHED_USER = "authed_user"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Store authenticated user in secure storage
     */
    fun storeUser(user: AuthedUser) {
        val userJson = json.encodeToString(user)
        secureStorage.set(KEY_AUTHED_USER, userJson)
    }

    /**
     * Get the authenticated user from secure storage
     * Returns null if no user is stored
     */
    fun getUser(): AuthedUser? {
        val userJson = secureStorage.string(KEY_AUTHED_USER) ?: return null
        return try {
            json.decodeFromString(userJson)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if the stored user is valid (exists and not expired)
     */
    fun hasValidUser(): Boolean {
        val user = getUser() ?: return false
        return !isUserExpired(user)
    }

    /**
     * Check if the user's token is expired
     */
    fun isUserExpired(user: AuthedUser?): Boolean {
        if(user == null) return true

        val currentTimeSeconds = Clock.System.now().epochSeconds
        return user.exp < currentTimeSeconds
    }

    /**
     * Update user if the current one is expired
     * Returns true if an update was needed and performed
     */
    fun updateUserIfExpired(newUser: AuthedUser): Boolean {
        val currentUser = getUser()

        return when {
            currentUser == null -> {
                storeUser(newUser)
                true
            }
            isUserExpired(currentUser) -> {
                storeUser(newUser)
                true
            }
            else -> false
        }
    }

    /**
     * Clear the stored user data
     */
    fun clearUser() {
        secureStorage.deleteObject(KEY_AUTHED_USER)
    }
}

// Example of platform-specific initializations:

// For Android:
// val kvault = KVault(context, "auth_secure_storage")
// val authRepository = AuthRepository(kvault)

// For iOS:
// val kvault = KVault("auth_secure_storage")
// val authRepository = AuthRepository(kvault)