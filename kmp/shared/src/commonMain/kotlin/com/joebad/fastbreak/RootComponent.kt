package com.joebad.fastbreak

import AuthRepository
import AuthedUser
import ProfileRepository
import ProtectedContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.items
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import com.joebad.fastbreak.ui.screens.LoginScreen
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.coroutines.SafeCoroutineScope
import com.joebad.fastbreak.coroutines.safeLaunch

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

class ProtectedComponent(
    componentContext: ComponentContext,
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val stack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = null,
        initialConfiguration = Config.Home,
        handleBackButton = true,
        childFactory = ::createChild
    )

    private fun createChild(config: Config, componentContext: ComponentContext): Child {
        return when (config) {
            is Config.Home -> Child.Home
            is Config.Leaderboard -> Child.Leaderboard
            is Config.Settings -> Child.Settings
            is Config.Profile -> Child.Profile
        }
    }

    fun goToSettings() {
        navigation.push(Config.Settings)
    }

    fun selectTab(tab: Config) {

        println("Current stack before: ${stack.items}")
        navigation.replaceAll(tab)
        println("Current stack after: ${stack.items}")
    }

    sealed class Config {
        object Home : Config()
        object Leaderboard : Config()
        object Settings : Config()
        object Profile : Config()
    }

    sealed class Child {
        object Home : Child()
        object Leaderboard : Child()
        object Settings : Child()
        object Profile : Child()
    }
}

class RootComponent(
    componentContext: ComponentContext,
    authRepository: AuthRepository
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val stack =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = if (shouldEnforceLogin(authRepository)) Config.Login else Config.Protected,
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
                        println("🔄 LoginComponent.onLoginClick called - navigating to Protected")
                        try {
                            navigation.replaceAll(Config.Protected)
                            println("✅ Navigation to Protected completed")
                        } catch (e: Exception) {
                            println("❌ Navigation failed: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                )
            )

            is Config.Protected -> Child.Protected(
                ProtectedComponent(
                    componentContext = componentContext,
                )
            )
        }
    }

    sealed class Config {
        object Login : Config()
        object Protected : Config()
    }

    sealed class Child {
        data class Login(val component: LoginComponent) : Child()
        data class Protected(val component: ProtectedComponent) : Child()
    }
}

fun createRootComponent(authRepository: AuthRepository): RootComponent {
    return RootComponent(DefaultComponentContext(LifecycleRegistry()), authRepository)
}

@Composable
fun App(
    rootComponent: RootComponent,
    onToggleTheme: (theme: Theme) -> Unit,
    themePreference: ThemePreference,
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    theme: Theme?
) {
    val colors = LocalColors.current;
    val lockedCard: MutableState<FastbreakSelectionState?> = mutableStateOf(null)

//    try {
//        Database.delete("fastbreak")
//    } catch (e: Exception) {
//        println("Database already deleted")
//    }

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
                            SafeCoroutineScope.safeLaunch {
                                instance.component.setLoading(true)
                                println("🔄 Starting login flow for user: ${au.userId}")
                                
                                val result = profileRepository.initializeProfile(au.userId, au.idToken)
                                val profile = result?.response
                                val baseUrl = result?.baseUrl
                                println("🔄 Profile initialization result: $profile")

                                if(profile != null) {
                                    val authedUser = AuthedUser(
                                        au.email,
                                        au.exp,
                                        au.idToken,
                                        profile.userId,
                                        profile.userName
                                    )
                                    authRepository.storeAuthedUser(authedUser)
                                    lockedCard.value = profile.lockedFastBreakCard
                                    println("✅ Profile initialized successfully, calling navigation")
                                    println("🔄 About to call onLoginClick()...")
                                    instance.component.onLoginClick()
                                    println("✅ onLoginClick() called successfully")
                                } else {
                                    println("❌ Profile initialization failed")
                                    instance.component.setError("Unable to initialize your profile. Please check your connection to $baseUrl and try again.")
                                }
                                instance.component.setLoading(false)
                                println("🔄 Login flow completed")
                            }
                        },
                        theme = theme,
                        error = instance.component.error,
                        isLoading = instance.component.isLoading
                    )

                    is RootComponent.Child.Protected ->
                        ProtectedContent(
                        instance.component,
                        onToggleTheme,
                        themePreference = themePreference,
                        authRepository,
                        onLogout = {
                            authRepository.clearUser()
                            rootComponent.goToLogin()
                        },
                        lockedCard = lockedCard.value
                    )
                }
            }
        }
    }
    ApplySystemBarsColor(colors.primary)
}