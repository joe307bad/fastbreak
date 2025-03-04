
//import compose.icons.TablerIcons
//import compose.icons.tablericons.LayoutAlignLeft
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.DrawerValue
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.rememberDrawerState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.DrawerContent
import com.joebad.fastbreak.DrawerItem
import com.joebad.fastbreak.ProtectedComponent
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.LockOpen
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
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { /* Your click handler */ },
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary,
                    modifier = Modifier
//                        .height(56.dp) // Adjust height as needed
                        .padding(8.dp) // Optional padding inside the FAB
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(20.dp) // Optional padding inside the Row() // Ensure content fills the FAB
                    ) {
                        CupertinoIcon(
                            imageVector = CupertinoIcons.Filled.LockOpen,
                            contentDescription = "Lock",
                            tint = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(24.dp) // Size of the icon
                        )
                        Spacer(modifier = Modifier.width(8.dp)) // Space between icon and text
                        Text(
                            text = "2,324", // Replace with your desired text
                            style = MaterialTheme.typography.body1,
                            maxLines = 1, // Ensure the text stays on one line
                            overflow = TextOverflow.Ellipsis // Handle overflow if text is too long
                        )
                    }
                }


            },
            floatingActionButtonPosition = FabPosition.Center,
//            topBar = {
//                TopAppBar(
//                    title = { Text("Fastbreak") },
//                    navigationIcon = {
//                        IconButton(onClick = {
//                            scope.launch {
//                                if (drawerState.isOpen) {
//                                    drawerState.close()
//                                } else {
//                                    drawerState.open()
//                                }
//                            }
//                        }) {
//                            Icon(
//                                imageVector = MenuDeep,
//                                contentDescription = "Menu",
//                                modifier = Modifier.graphicsLayer(scaleX = -1f)
//                            )
//                        }
//                    }
//                )
//            },
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
                            HomeScreen()
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
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (drawerState.isOpen) {
                                    drawerState.close()
                                } else {
                                    drawerState.open()
                                }
                            }
                        },
                        modifier = Modifier
                            .padding(16.dp)
                            .offset(x = 0.dp, y = 0.dp),
                        containerColor = MaterialTheme.colors.primary,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            tint = MaterialTheme.colors.onPrimary,
                            imageVector = MenuDeep,
                            contentDescription = "Menu",
                            modifier = Modifier.graphicsLayer(scaleX = -1f)
                        )
                    }
                }
            }
        }
    }
}