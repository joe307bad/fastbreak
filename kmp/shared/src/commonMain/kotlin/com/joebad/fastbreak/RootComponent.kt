package com.joebad.fastbreak

import ProtectedContent
import Theme
import ThemeSelector
import Title
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi

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
                        themePreference = themePreference
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

@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun DrawerContent(
    items: List<DrawerItem>,
    themePreference: ThemePreference,
    onToggleTheme: (theme: Theme) -> Unit
) {
    val colors = LocalColors.current;
    Column(
        modifier = Modifier.fillMaxSize().background(color = colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            Column {
                Column(modifier = Modifier.background(color = colors.primary)) {
                    Column(modifier = Modifier.padding(10.dp, top = 20.dp).fillMaxWidth()) {
                        Title("FASTBREAK")
                        Column(
                            modifier = Modifier.padding(
                                start = 10.dp,
                                top = 20.dp,
                                bottom = 20.dp
                            )
                        ) {
                            Row {
                                Icon(
                                    Icons.Default.Person,
                                    tint = colors.onPrimary,
                                    contentDescription = "User"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "joebad",
                                    color = colors.onPrimary,
                                    style = MaterialTheme.typography.h6
                                )
                            }
                        }
                    }
                }
                Column(modifier = Modifier.padding(10.dp, top = 20.dp).fillMaxWidth()) {
                    Text(
                        text = "My Stat Sheet",
                        color = colors.text,
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight().width(80.dp)// Make column fill the row height
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(colors.accent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp) // Adjust padding as needed
                                    .fillMaxHeight()
                                    .width(60.dp), // Make box fill the height of the row
                                contentAlignment = Alignment.Center // Center the text vertically
                            ) {
                                Text(
                                    text = "10,065",
                                    color = colors.onAccent,
                                    style = MaterialTheme.typography.body1,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Days in a row locking in your FastBreak card",
                            color = colors.text,
                            style = MaterialTheme.typography.body1
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight().width(80.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(colors.accent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp) // Adjust padding as needed
                                    .fillMaxHeight()
                                    .width(60.dp), // Make box fill the height of the row
                                contentAlignment = Alignment.Center // Center the text vertically
                            ) {
                                Text(
                                    text = "365",
                                    color = colors.onAccent,
                                    style = MaterialTheme.typography.body1,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Your highest Fastbreak card",
                            color = colors.text,
                            style = MaterialTheme.typography.body1
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight().width(80.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(colors.accent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp) // Adjust padding as needed
                                    .fillMaxHeight()
                                    .width(60.dp), // Make box fill the height of the row
                                contentAlignment = Alignment.Center // Center the text vertically
                            ) {
                                Text(
                                    text = "123",
                                    color = colors.onAccent,
                                    style = MaterialTheme.typography.body1,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Days in a row with a winning pick",
                            color = colors.text,
                            style = MaterialTheme.typography.body1
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight().width(80.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(colors.accent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp) // Adjust padding as needed
                                    .fillMaxHeight()
                                    .width(60.dp), // Make box fill the height of the row
                                contentAlignment = Alignment.Center // Center the text vertically
                            ) {
                                Text(
                                    text = "34",
                                    color = colors.onAccent,
                                    style = MaterialTheme.typography.body1,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Number of perfect Fastbreak cards",
                            color = colors.text,
                            style = MaterialTheme.typography.body1
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight().width(80.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(colors.accent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp) // Adjust padding as needed
                                    .fillMaxHeight()
                                    .width(60.dp), // Make box fill the height of the row
                                contentAlignment = Alignment.Center // Center the text vertically
                            ) {
                                Text(
                                    text = "87",
                                    color = colors.onAccent,
                                    style = MaterialTheme.typography.body1,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Number of weekly wins",
                            color = colors.text,
                            style = MaterialTheme.typography.body1
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = "Theme",
                color = colors.text,
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(5.dp).fillMaxWidth()
            )
            ThemeSelector(themePreference = themePreference, onToggleTheme = onToggleTheme)
        }
    }
}