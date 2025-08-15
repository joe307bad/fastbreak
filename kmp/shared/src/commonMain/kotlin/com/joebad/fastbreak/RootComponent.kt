package com.joebad.fastbreak

import AuthRepository
import GoogleUser
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
import com.joebad.fastbreak.data.cache.FastbreakCache
import com.joebad.fastbreak.data.global.AppDataAction
import com.joebad.fastbreak.data.global.AppDataViewModel
import com.joebad.fastbreak.data.profile.ProfileAction
import com.joebad.fastbreak.data.profile.ProfileSideEffect
import com.joebad.fastbreak.data.profile.ProfileViewModel
import com.joebad.fastbreak.ui.screens.LoginScreen
import com.joebad.fastbreak.ui.theme.LocalColors

fun shouldEnforceLogin(authRepository: AuthRepository): Boolean {
    val authedUser = authRepository.getUser()
    return authedUser == null
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
    cache: FastbreakCache
) : ComponentContext by componentContext, AppDependencies {

    override val appDataViewModel = AppDataViewModel(cache, authRepository)
    override val profileViewModel = ProfileViewModel(authRepository)

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
        // The new LoadDailyData action will be called from the UI when needed
        // with the appropriate dateString and userId parameters
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
                    onLogout = { 
                        authRepository.clearUser()
                        navigation.replaceAll(Config.Login) 
                    }
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
    val cache = CacheInitializer.createFastbreakCache()
    return RootComponent(DefaultComponentContext(LifecycleRegistry()), authRepository, cache)
}

interface AppDependencies {
    val appDataViewModel: AppDataViewModel
    val profileViewModel: ProfileViewModel
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
        dependencies.profileViewModel.container.sideEffectFlow.collect { sideEffect ->
            when (sideEffect) {
                ProfileSideEffect.InitializationComplete -> {
                    val userId = dependencies.authRepository.getUser()!!.userId
                    dependencies.appDataViewModel.handleAction(
                        AppDataAction.LoadStats(userId)
                    )
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
                    is RootComponent.Child.Login -> {
                        val profileState by dependencies.profileViewModel.container.stateFlow.collectAsState()
                        
                        LoginScreen(
                            goToHome = { au ->
                                val googleUser = GoogleUser(
                                    au.email,
                                    au.exp,
                                    au.idToken
                                )
                                dependencies.profileViewModel.handleAction(
                                    ProfileAction.InitializeProfile(googleUser)
                                )
                            },
                            theme = theme,
                            error = profileState.error ?: instance.component.error,
                            isLoading = profileState.isLoading || instance.component.isLoading
                        )
                    }

                    is RootComponent.Child.Home -> {
                        val appDataState by dependencies.appDataViewModel.container.stateFlow.collectAsState()
                        HomeScreen(
                            appDataState = appDataState,
                            onLogout = instance.component.onLogout
                        )
                    }
                }
            }
        }
    }
    ApplySystemBarsColor(colors.primary)
}