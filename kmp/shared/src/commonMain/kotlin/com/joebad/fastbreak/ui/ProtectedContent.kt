//import compose.icons.TablerIcons
//import compose.icons.tablericons.LayoutAlignLeft
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.DrawerContent
import com.joebad.fastbreak.DrawerItem
import com.joebad.fastbreak.ProtectedComponent
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.LockOpen
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalAnimationApi::class, ExperimentalDecomposeApi::class)
@Composable
fun ProtectedContent(component: ProtectedComponent, onToggleTheme: () -> Unit) {

    val childStack by component.stack.subscribeAsState()
    val activeChild = childStack.active
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val colors = LocalColors.current;

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


    val listState = rememberLazyListState()

    val scrollOffset by remember {
        derivedStateOf {
            min(1f, listState.firstVisibleItemScrollOffset / 300f)
        }
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = 1f - scrollOffset,
        label = "Text Fade Animation"
    )

    ModalDrawer(
        modifier = Modifier.background(color = colors.background),
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(items = drawerItems)
        }
    ) {
        Scaffold(
            modifier = Modifier.background(color = colors.background),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { /* Your click handler */ },
                    backgroundColor = colors.primary,
                    contentColor = colors.onPrimary
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        CupertinoIcon(
                            imageVector = CupertinoIcons.Filled.LockOpen,
                            contentDescription = "Lock",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "2,324",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }


            },
            floatingActionButtonPosition = FabPosition.Center,
            bottomBar = {
                BottomNavigation(backgroundColor = colors.primary) {
                    BottomNavigationItem(
                        icon = {
                            Icon(
                                Icons.Default.Home,
                                tint = colors.onPrimary,
                                contentDescription = "Home"
                            )
                        },
                        label = { Text("Home", color = colors.onPrimary) },
                        selected = activeChild::class == ProtectedComponent.Child.Home::class,
                        onClick = { component.selectTab(ProtectedComponent.Config.Home) }
                    )
                    BottomNavigationItem(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                tint = colors.onPrimary,
                                contentDescription = "Leaderboard"
                            )
                        },
                        label = { Text("Leaderboard", color = colors.onPrimary) },
                        selected = activeChild::class == ProtectedComponent.Child.Leaderboard::class,
                        onClick = { component.selectTab(ProtectedComponent.Config.Leaderboard) }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).background(color = colors.background)) {
                Children(
                    stack = childStack,
                ) { child ->
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                onToggleTheme();
//                                if (drawerState.isOpen) {
//                                    drawerState.close()
//                                } else {
//                                    drawerState.open()
//                                }
                            }
                        },
                        modifier = Modifier
                            .padding(16.dp)
                            .offset(x = (-5).dp, y = 0.dp)
                            .zIndex(3f),
                        containerColor = colors.primary,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            tint = colors.onPrimary,
                            imageVector = MenuDeep,
                            contentDescription = "Menu",
                            modifier = Modifier.graphicsLayer(scaleX = -1f)
                        )
                    }
                    RoundedBottomHeaderBox(
                        "FASTBREAK",
                        "Daily sports fantasy games and trivia",
                        "Season 1 • Week 50 • Day 3",
                        animatedAlpha
                    )
                    Column(modifier = Modifier.zIndex(2f)) {
                        when (child.instance) {
                            is ProtectedComponent.Child.Home -> {
                                HomeScreen(listState)
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
}