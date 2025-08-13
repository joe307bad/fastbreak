package com.joebad.fastbreak

import AuthRepository
import AuthedUser
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.joebad.fastbreak.ui.screens.LoginScreen
import com.joebad.fastbreak.ui.screens.TokenDisplayScreen
import com.joebad.fastbreak.ui.theme.LocalColors
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotbase.Database
import kotlinx.serialization.json.Json

fun shouldEnforceLogin(authRepository: AuthRepository): Boolean {
    val authedUser = authRepository.getUser()
    return authRepository.isUserExpired(authedUser)
}

class LoginComponent(
    componentContext: ComponentContext,
    val onLoginClick: () -> Unit
) : ComponentContext by componentContext {
    private var _error by mutableStateOf<String?>(null)
    val error: String? get() = _error
    
    private var _isLoading by mutableStateOf(false)
    val isLoading: Boolean get() = _isLoading
    
    fun setError(message: String) {
        _error = message
        _isLoading = false
    }
    
    fun clearError() {
        _error = null
    }
    
    fun setLoading(loading: Boolean) {
        _isLoading = loading
        if (loading) {
            _error = null
        }
    }
}

class TokenDisplayComponent(
    componentContext: ComponentContext,
    val onLogout: () -> Unit
) : ComponentContext by componentContext

class RootComponent(
    componentContext: ComponentContext,
    val authRepository: AuthRepository,
    val database: kotbase.Database? = null,
    val httpClient: io.ktor.client.HttpClient? = null
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val stack =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = if (shouldEnforceLogin(authRepository)) Config.Login else Config.TokenDisplay,
            handleBackButton = true,
            childFactory = ::createChild,
        )

    @OptIn(DelicateDecomposeApi::class)
    fun goToLogin() {
        navigation.replaceAll(Config.Login)
    }

    private fun createChild(config: Config, componentContext: ComponentContext): Child {
        return when (config) {
            is Config.Login -> Child.Login(
                LoginComponent(
                    componentContext = componentContext,
                    onLoginClick = {
                        println("🔄 LoginComponent.onLoginClick called - navigating to TokenDisplay")
                        try {
                            navigation.replaceAll(Config.TokenDisplay)
                            println("✅ Navigation to TokenDisplay completed")
                        } catch (e: Exception) {
                            println("❌ Navigation failed: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                )
            )

            is Config.TokenDisplay -> Child.TokenDisplay(
                TokenDisplayComponent(
                    componentContext = componentContext,
                    onLogout = {
                        println("🔄 TokenDisplayComponent.onLogout called - navigating to Login")
                        navigation.replaceAll(Config.Login)
                    }
                )
            )
        }
    }

    sealed class Config {
        object Login : Config()
        object TokenDisplay : Config()
    }

    sealed class Child {
        data class Login(val component: LoginComponent) : Child()
        data class TokenDisplay(val component: TokenDisplayComponent) : Child()
    }
}

fun createRootComponent(
    authRepository: AuthRepository,
): RootComponent {
    // Initialize database for caching
    val database = try {
        Database("fastbreak_cache")
    } catch (e: Exception) {
        println("Failed to initialize database: ${e.message}")
        null // Gracefully handle database initialization failure
    }

    // Initialize HTTP client for API calls
    val httpClient = try {
        HttpClient {
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
    } catch (e: Exception) {
        println("Failed to initialize HTTP client: ${e.message}")
        null // Gracefully handle HTTP client initialization failure
    }

    return RootComponent(DefaultComponentContext(LifecycleRegistry()), authRepository, database, httpClient)
}

@Composable
fun App(
    rootComponent: RootComponent,
    onToggleTheme: (theme: Theme) -> Unit,
    themePreference: ThemePreference,
    authRepository: AuthRepository,
    theme: Theme?
) {
    val colors = LocalColors.current

    MaterialTheme {
        Surface(color = colors.background) {
            val childStack = rootComponent.stack.subscribeAsState()

            Children(
                stack = childStack.value,
                animation = stackAnimation(fade())
            ) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.Login -> LoginScreen(
                        goToHome = { au ->
                            val authedUser = AuthedUser(
                                au.email,
                                au.exp,
                                au.idToken,
//                                "Unknown" // Default username since we're not initializing profile
                            )
                            rootComponent.authRepository.storeAuthedUser(authedUser)
                            instance.component.onLoginClick()
                        },
                        theme = theme,
                        error = instance.component.error,
                        isLoading = instance.component.isLoading
                    )

                    is RootComponent.Child.TokenDisplay -> TokenDisplayScreen(
                        authRepository = rootComponent.authRepository,
                        database = rootComponent.database,
                        httpClient = rootComponent.httpClient,
                        onLogout = {
                            rootComponent.authRepository.clearUser()
                            instance.component.onLogout()
                        }
                    )
                }
            }
        }
    }
    ApplySystemBarsColor(colors.primary)
}