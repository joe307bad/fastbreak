
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.DrawerContent
import com.joebad.fastbreak.DrawerItem
import com.joebad.fastbreak.ProtectedComponent
import com.joebad.fastbreak.ui.TeamCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class, ExperimentalDecomposeApi::class)
@Composable
fun ProtectedContent(component: ProtectedComponent) {

    val childStack by component.stack.subscribeAsState()
    val activeChild = childStack.active
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
            Box(modifier = Modifier.padding(paddingValues)) {
                Children(
                    stack = childStack,
                ) { child ->
                    when (child.instance) {
                        is ProtectedComponent.Child.Home -> {
                            TeamCard()
                        }
                        is ProtectedComponent.Child.Leaderboard -> {
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
            }
        }
    }
}