import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.rememberDrawerState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.ProtectedComponent
import com.joebad.fastbreak.ThemePreference
import com.joebad.fastbreak.ui.theme.LocalColors
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalAnimationApi::class, ExperimentalDecomposeApi::class)
@Composable
fun ProtectedContent(
    component: ProtectedComponent,
    onToggleTheme: (theme: Theme) -> Unit,
    themePreference: ThemePreference
) {

    val childStack by component.stack.subscribeAsState()
    val activeChild = childStack.active
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val colors = LocalColors.current;

    val showLastweeksFastbreakCard = remember { mutableStateOf(false) }
    val showModal = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()

    val scrollOffset by remember {
        derivedStateOf {
            min(1f, listState.firstVisibleItemScrollOffset / 300f)
        }
    }

    val animatedAlpha: Float by animateFloatAsState(
        targetValue = 1f - scrollOffset,
        label = "Text Fade Animation"
    )

    var locked by remember { mutableStateOf(false) }

    val fastbreakCard = FastbreakCardViewModel();

    BlurredScreen(
        locked = true,
        onLocked = { },
        showLastweeksFastbreakCard.value,
        onDismiss = { showLastweeksFastbreakCard.value = false },
        date = "2025-10-11",
        hideLockCardButton = true,
        title = "Yesterday's Fastbreak Card",
        showCloseButton = true
    )
    BlurredScreen(
        locked,
        onLocked = { locked = true },
        showModal.value,
        onDismiss = { showModal.value = false },
        date = "2025-10-11"
    )
    ModalDrawer(
        modifier = Modifier.background(color = colors.background)
            .then(if (showModal.value) Modifier.blur(16.dp) else Modifier),
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                onShowLastweeksFastbreakCard = { showLastweeksFastbreakCard.value = true },
                themePreference = themePreference,
                onToggleTheme = onToggleTheme,
                fastbreakCard
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.background(color = colors.background),
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
                                if (drawerState.isOpen) {
                                    drawerState.close()
                                } else {
                                    drawerState.open()
                                }
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
                    Column(modifier = Modifier.zIndex(2f)) {
                        when (child.instance) {
                            is ProtectedComponent.Child.Home -> {
                                HomeScreen(locked, listState, animatedAlpha, showModal, fastbreakCard)
                            }

                            is ProtectedComponent.Child.Leaderboard -> {
                                LeaderboardScreen(scrollState)
                            }
                        }
                    }
                }
            }
        }
    }
}