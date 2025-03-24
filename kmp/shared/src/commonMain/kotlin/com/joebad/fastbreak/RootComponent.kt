package com.joebad.fastbreak

import DailyFastbreak
import FastbreakStateRepository
import ProtectedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
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
import com.joebad.fastbreak.ui.theme.LocalColors
import io.ktor.client.HttpClient
import kotbase.Database
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun shouldEnforceLogin(): Boolean {
    return !BuildKonfig.IS_DEBUG
}

class LoginComponent(
    componentContext: ComponentContext,
    val onLoginClick: () -> Unit
) : ComponentContext by componentContext


@Composable
fun LoginContent(component: LoginComponent) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "LOGO",
                style = MaterialTheme.typography.h3
            )
        }

        Button(
            onClick = component.onLoginClick,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Login")
        }
    }
}

class ProtectedComponent(
    componentContext: ComponentContext
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
        }
    }

    fun selectTab(tab: Config) {

        println("Current stack before: ${stack.items}")
        navigation.replaceAll(tab)
        println("Current stack after: ${stack.items}")
    }

    sealed class Config {
        object Home : Config()
        object Leaderboard : Config()
    }

    sealed class Child {
        object Home : Child()
        object Leaderboard : Child()
    }
}

class RootComponent(
    componentContext: ComponentContext
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val stack =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = if (shouldEnforceLogin()) Config.Login else Config.Protected,
            handleBackButton = true,
            childFactory = ::createChild,
        )

    private fun createChild(config: Config, componentContext: ComponentContext): Child {
        return when (config) {
            is Config.Login -> Child.Login(
                LoginComponent(
                    componentContext = componentContext,
                    onLoginClick = { navigation.push(Config.Protected) }
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

fun createRootComponent(): RootComponent {
    return RootComponent(DefaultComponentContext(LifecycleRegistry()))
}

@Composable
fun App(
    rootComponent: RootComponent,
    onToggleTheme: (theme: Theme) -> Unit,
    themePreference: ThemePreference
) {
    val colors = LocalColors.current;


    try {
//        Database.delete("fastbreak")
    } catch(e: Exception) {
        println("Database already deleted")
    }

    val db = Database("fastbreak");

    val dailyFastbreakRepository = FastbreakStateRepository(
        db,
        HttpClient()
    )

    var fastbreakState by remember { mutableStateOf<DailyFastbreak?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

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
                    is RootComponent.Child.Login -> LoginContent(instance.component)
                    is RootComponent.Child.Protected -> ProtectedContent(
                        instance.component,
                        onToggleTheme,
                        themePreference = themePreference,
                        fastbreakState
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