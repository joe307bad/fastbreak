
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimator
import com.arkivanov.decompose.extensions.compose.stack.animation.isFront
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimator
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.DrawerContent
import com.joebad.fastbreak.DrawerItem
import com.joebad.fastbreak.ProtectedComponent
import com.joebad.fastbreak.ui.TeamCard
import kotlinx.coroutines.launch

@ExperimentalDecomposeApi
private fun iosLikeSlide(animationSpec: FiniteAnimationSpec<Float> = tween()): StackAnimator =
    stackAnimator(animationSpec = animationSpec) { factor, direction, content ->
        content(
            Modifier.then(
                if (direction.isFront) {
                    Modifier
                } else {
                    Modifier.graphicsLayer(alpha = factor + 1F) // Apply fade effect
                        .offset(x = if (direction.isFront) factor * 16.dp else factor * 16.dp) // Apply slide effect
                }
            )
        )
    }

@OptIn(ExperimentalAnimationApi::class, ExperimentalDecomposeApi::class)
@Composable
fun ProtectedContent(component: ProtectedComponent) {

    val childStack by component.stack.subscribeAsState()
    val activeChild = childStack.active
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
//    println("Child stack size: ${childStack.items.size}")
//
//    println("Active child: $activeChild")
//    println("Expected Home: ${ProtectedComponent.Child.Home}")
//    println("Match: ${activeChild == ProtectedComponent.Child.Home}")

//    LaunchedEffect(childStack.active.instance) {
//        println("Navigation updated: ${childStack.active.instance}")
//    }

    // Drawer items
    val drawerItems = listOf(
        DrawerItem(
            title = "Profile",
            icon = Icons.Default.Person,
            onClick = { /* Handle profile click */ }
        ),
        DrawerItem(
            title = "Settings",
            icon = Icons.Default.Settings,
            onClick = { /* Handle settings click */ }
        )
    )

    ModalDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(items = drawerItems)
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Fastbreak") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                if (drawerState.isOpen) {
                                    drawerState.close()
                                } else {
                                    drawerState.open()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            bottomBar = {
                BottomNavigation {
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = activeChild::class == ProtectedComponent.Child.Home::class,
                        onClick = { component.selectTab(ProtectedComponent.Config.Home) }
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Leaderboard") },
                        label = { Text("Leaderboard") },
                        selected = activeChild::class == ProtectedComponent.Child.Leaderboard::class,
                        onClick = { component.selectTab(ProtectedComponent.Config.Leaderboard) }
                    )
                }
            }
        ) { paddingValues ->
            println("childStack: $childStack")
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(paddingValues)
//            ) {
            Text(
                "Test Text",
                color = Color.Red,
                style = MaterialTheme.typography.h1,
                modifier = Modifier.padding(vertical = 16.dp)
            )
//                println("?")
                Children(
                    stack = childStack,
                    animation = stackAnimation(iosLikeSlide())

                ) { child ->
                    println("child rendered: ${child.instance}")
                    when (child.instance) {
                        is ProtectedComponent.Child.Home -> {
                            println("child.instance: ${child.instance}")
                            TeamCard()
//                            Column(
//                                modifier = Modifier
//                                    .fillMaxSize()
//                                    .padding(horizontal = 16.dp)
//                            ) {

                                // TeamCard with weight to fill available space
//                                Box(modifier = Modifier.weight(1f)) {
//                                    TeamCard()
//                                }
//                            }
                        }
                        is ProtectedComponent.Child.Leaderboard -> {
                            println("child.instance: ${child.instance}")
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Leaderboard Screen", style = MaterialTheme.typography.h1)
                            }
                        }
                    }
                }
//            }
        }
    }
}