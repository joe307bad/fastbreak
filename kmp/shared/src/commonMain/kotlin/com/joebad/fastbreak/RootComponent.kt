package com.joebad.fastbreak

import AuthRepository
import AuthedUser
import HomeScreen
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.joebad.fastbreak.data.cache.CacheInitializer
import com.joebad.fastbreak.data.global.AppDataAction
import com.joebad.fastbreak.data.global.AppDataSideEffect
import com.joebad.fastbreak.data.global.AppDataViewModel
import com.joebad.fastbreak.ui.screens.LoginScreen
import com.joebad.fastbreak.ui.theme.LocalColors

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

class HomeComponent(
    componentContext: ComponentContext,
    val onLogout: () -> Unit
) : ComponentContext by componentContext

class RootComponent(
    componentContext: ComponentContext,
    override val authRepository: AuthRepository,
    database: kotbase.Database,
    httpClient: io.ktor.client.HttpClient
) : ComponentContext by componentContext, AppDependencies {

    override val appDataViewModel = AppDataViewModel(database, httpClient)

    val navigation = StackNavigation<Config>()
    
    override val onNavigateToHome: () -> Unit = {
        navigation.replaceAll(Config.Home)
    }

    override val stack =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = if (shouldEnforceLogin(authRepository)) Config.Login else Config.Home,
            handleBackButton = true,
            childFactory = ::createChild,
        )

    @OptIn(DelicateDecomposeApi::class)
    fun goToLogin() {
        navigation.replaceAll(Config.Login)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun goToHome() {
        appDataViewModel.handleAction(AppDataAction.InitializeAppWithData)
    }

    private fun createChild(config: Config, componentContext: ComponentContext): Child {
        return when (config) {
            is Config.Login -> Child.Login(
                LoginComponent(
                    componentContext = componentContext,
                    onLoginClick = { 
                        // Don't navigate directly, let the side effect handle navigation
                        // The goToHome function will initialize data and trigger navigation
                    }
                )
            )

            is Config.Home -> Child.Home(
                HomeComponent(
                    componentContext = componentContext,
                    onLogout = { navigation.replaceAll(Config.Login) }
                )
            )
        }
    }

    sealed class Config {
        object Login : Config()
        object Home : Config()
    }

    sealed class Child {
        data class Login(val component: LoginComponent) : Child()
        data class Home(val component: HomeComponent) : Child()
    }
}

fun createRootComponent(
    authRepository: AuthRepository,
): RootComponent {
    val (database, httpClient) = CacheInitializer.initializeCache()
    return RootComponent(DefaultComponentContext(LifecycleRegistry()), authRepository, database, httpClient)
}

interface AppDependencies {
    val appDataViewModel: AppDataViewModel
    val onNavigateToHome: () -> Unit
    val stack: Value<ChildStack<*, RootComponent.Child>>
    val authRepository: AuthRepository
}

@Composable
fun App(
    dependencies: AppDependencies,
    theme: Theme?
) {
    val colors = LocalColors.current

    LaunchedEffect(Unit) {
        dependencies.appDataViewModel.container.sideEffectFlow.collect { sideEffect ->
            when (sideEffect) {
                AppDataSideEffect.NavigateToHome -> {
                    dependencies.onNavigateToHome()
                }
            }
        }
    }

    MaterialTheme {
        Surface(color = colors.background) {
            val childStack = dependencies.stack.subscribeAsState()

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
                            )
                            dependencies.authRepository.storeAuthedUser(authedUser)
                            dependencies.onNavigateToHome()
                        },
                        theme = theme,
                        error = instance.component.error,
                        isLoading = instance.component.isLoading
                    )

                    is RootComponent.Child.Home -> {
                        val appDataState by dependencies.appDataViewModel.container.stateFlow.collectAsState()
                        HomeScreen(cacheStatus = appDataState.cacheStatus)
                    }
                }
            }
        }
    }
    ApplySystemBarsColor(colors.primary)
}