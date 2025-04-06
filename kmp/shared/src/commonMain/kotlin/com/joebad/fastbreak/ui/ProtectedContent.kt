
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.joebad.fastbreak.Theme
import com.joebad.fastbreak.ThemePreference
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakStateRepository
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakViewModel
import com.joebad.fastbreak.model.dtos.DailyFastbreak
import com.joebad.fastbreak.onLock
import com.joebad.fastbreak.ui.theme.LocalColors
import io.ktor.client.HttpClient
import kotbase.Database
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.min

@OptIn(ExperimentalAnimationApi::class, ExperimentalDecomposeApi::class)
@Composable
fun ProtectedContent(
    component: ProtectedComponent,
    onToggleTheme: (theme: Theme) -> Unit,
    themePreference: ThemePreference,
    authRepository: AuthRepository,
    onLogout: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var dailyFastbreak by remember { mutableStateOf<DailyFastbreak?>(null) }
    var viewModel by remember { mutableStateOf<FastbreakViewModel?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val db = Database("fastbreak");

    val currentDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    LaunchedEffect(key1 = Unit) {
        coroutineScope.launch {
            try {
                val dailyFastbreakRepository = FastbreakStateRepository(
                    db,
                    HttpClient(),
                    authRepository
                )
                val state = dailyFastbreakRepository.getDailyFastbreakState(currentDate)
                dailyFastbreak = state

                viewModel = FastbreakViewModel(
                    db,
                    { newState -> onLock(dailyFastbreakRepository, coroutineScope, newState) },
                    currentDate.replace("-", ""),
                    authRepository
                )

                print(dailyFastbreak)
            } catch (e: Exception) {
                error = "Failed to fetch state: ${e.message}"
            }
        }
    }

    val state = viewModel?.container?.stateFlow?.collectAsState()?.value;
    val locked = state?.locked ?: false;
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

    val today = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()

    BlurredScreen(
        locked = true,
        onLocked = { },
        showLastweeksFastbreakCard.value,
        onDismiss = { showLastweeksFastbreakCard.value = false },
        date = "2025-10-11",
        hideLockCardButton = true,
        title = "Yesterday's Fastbreak Card",
        showCloseButton = true,
    )
    BlurredScreen(
        locked,
        onLocked = { viewModel?.lockCard() },
        showModal.value,
        onDismiss = { showModal.value = false },
        date = today,
        fastbreakViewModel = viewModel
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
                goToSettings = {
                    scope.launch {
                        launch { drawerState.close() }
                        launch {
                            if (activeChild.instance != ProtectedComponent.Child.Settings) {
                                component.goToSettings()
                            }
                        }
                    }
                }
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
                            is ProtectedComponent.Child.Settings -> {
                                SettingsScreen(onLogout = onLogout)
                            }

                            is ProtectedComponent.Child.Home -> {
                                HomeScreen(
                                    locked,
                                    listState,
                                    animatedAlpha,
                                    showModal,
                                    dailyFastbreak,
                                    viewModel
                                )
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