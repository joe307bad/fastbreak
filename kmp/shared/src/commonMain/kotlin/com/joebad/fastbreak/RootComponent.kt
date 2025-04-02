package com.joebad.fastbreak

import AuthRepository
import ProtectedContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakViewModel
import com.joebad.fastbreak.model.dtos.DailyFastbreak
import com.joebad.fastbreak.ui.screens.LoginScreen
import com.joebad.fastbreak.ui.theme.LocalColors
import io.ktor.client.HttpClient
import kotbase.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun shouldEnforceLogin(authRepository: AuthRepository): Boolean {
    val authedUser = authRepository.getUser()
    return authRepository.isUserExpired(authedUser) // ?: true; //!BuildKonfig.IS_DEBUG
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
    }

    sealed class Child {
        object Home : Child()
        object Leaderboard : Child()
        object Settings : Child()
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
                    onLoginClick = { navigation.replaceAll(Config.Protected) }
                )
            )

            is Config.Protected -> Child.Protected(
                ProtectedComponent(
                    componentContext = componentContext
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
    theme: Theme?
) {
    val colors = LocalColors.current;

    try {
        //Database.delete("fastbreak")
    } catch (e: Exception) {
        println("Database already deleted")
    }

    val db = Database("fastbreak");

    val dailyFastbreakRepository = FastbreakStateRepository(
        db,
        HttpClient(),
        authRepository
    )

    val coroutineScope = rememberCoroutineScope()
    var fastbreakState by remember { mutableStateOf<DailyFastbreak?>(null) }

    val viewModel = remember {
        FastbreakViewModel(
            db,
            { state -> onLock(dailyFastbreakRepository, coroutineScope, state) }
        )
    }

    var error by remember { mutableStateOf<String?>(null) }

    val currentDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    LaunchedEffect(key1 = Unit) {
        coroutineScope.launch {
            try {
                fastbreakState = dailyFastbreakRepository.getDailyFastbreakState(currentDate)
                print(fastbreakState);
            } catch (e: Exception) {
                error = "Failed to fetch state: ${e.message}"
            }
        }
    }

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
                            authRepository.storeUser(authedUser)
                            instance.component.onLoginClick()
                        },
                        theme = theme
                    )

                    is RootComponent.Child.Protected -> ProtectedContent(
                        instance.component,
                        onToggleTheme,
                        themePreference = themePreference,
                        fastbreakState,
                        viewModel,
                        authRepository,
                        onLogout = {
                            authRepository.clearUser()
                            rootComponent.goToLogin()
                        }
                    )
                }
            }
        }
    }
    ApplySystemBarsColor(colors.primary)
}

data class DrawerItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)