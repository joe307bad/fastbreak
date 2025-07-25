package com.joebad.fastbreak

import AuthRepository
import ProfileRepository
import ProtectedContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakStateRepository
import com.joebad.fastbreak.ui.screens.LoginScreen
import com.joebad.fastbreak.ui.theme.LocalColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun shouldEnforceLogin(authRepository: AuthRepository): Boolean {
    val authedUser = authRepository.getUser()
    return authRepository.isUserExpired(authedUser)
}

class LoginComponent(
    componentContext: ComponentContext,
    val onLoginClick: () -> Unit
) : ComponentContext by componentContext

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
                        navigation.replaceAll(Config.Protected)
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

fun onLock(
    dailyFastbreakRepository: FastbreakStateRepository,
    coroutineScope: CoroutineScope,
    state: FastbreakSelectionState
) {
    coroutineScope.launch {
        try {
            val result = dailyFastbreakRepository.lockCardApi(state)
            print(result);
        } catch (e: Exception) {
            print(e.message);
//            error = "API failed to lock card: ${e.message}"
        }
    }
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
                        goToHome = { authedUser ->
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    lockedCard.value = authRepository.storeUser(authedUser)
                                    instance.component.onLoginClick()
                                } catch (e: Exception) {
                                    // Show error message instead of navigating
                                    println("Login failed: ${e.message}")
                                    // TODO: Show error UI to user
                                }
                            }
                        },
                        theme = theme
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