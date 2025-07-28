
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.DrawerValue
import androidx.compose.material.ModalDrawer
import androidx.compose.material.rememberDrawerState
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
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.ProtectedComponent
import com.joebad.fastbreak.Theme
import com.joebad.fastbreak.ThemePreference
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakStateRepository
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakViewModel
import com.joebad.fastbreak.model.dtos.DailyFastbreak
import com.joebad.fastbreak.onLock
import com.joebad.fastbreak.ui.SimpleBottomSheetExample
import com.joebad.fastbreak.ui.help.HelpData
import com.joebad.fastbreak.ui.help.HelpPage
import com.joebad.fastbreak.ui.theme.LocalColors
import io.ktor.client.HttpClient
import kotbase.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun ProtectedContent(
    component: ProtectedComponent,
    onToggleTheme: (theme: Theme) -> Unit,
    themePreference: ThemePreference,
    authRepository: AuthRepository,
    lockedCard: FastbreakSelectionState? = null,
    onLogout: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var dailyFastbreak by remember { mutableStateOf<DailyFastbreak?>(null) }
    var viewModel by remember { mutableStateOf<FastbreakViewModel?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var bottomSheetHelpPage by remember { mutableStateOf(HelpPage.HOME) }
    
    val openHelpSheet = { helpPage: HelpPage ->
        bottomSheetHelpPage = helpPage
        showBottomSheet = true
    }

    val selectedDate = "20250728"

    val db = remember { Database("fastbreak") }
    val httpClient = remember { HttpClient() }
    val dailyFastbreakRepository =
        remember { FastbreakStateRepository(db, httpClient, authRepository) }

    val syncData: suspend (forceUpdate: Boolean) -> Unit = { forceUpdate ->
        try {
            error = null // Clear previous errors
            val state = dailyFastbreakRepository.getDailyFastbreakState(selectedDate, forceUpdate)
            dailyFastbreak = state
            val statSheetItems = state?.statSheet?.items
            viewModel = FastbreakViewModel(
                db,
                { newState -> onLock(dailyFastbreakRepository, coroutineScope, newState) },
                selectedDate,
                authRepository,
                statSheetItems,
                selectedDate,
                lockedCard,
                state?.lastLockedCardResults
            )
        } catch (e: Exception) {
            // Enhanced error handling for better user experience
            error = when {
                e.message?.contains("network", ignoreCase = true) == true -> "Network connection failed. Please check your internet connection."
                e.message?.contains("timeout", ignoreCase = true) == true -> "Request timed out. Please try again."
                e.message?.contains("unauthorized", ignoreCase = true) == true -> "Authentication failed. Please log in again."
                else -> "Failed to fetch data: ${e.message ?: "Unknown error"}"
            }
            // Reset state on error
            dailyFastbreak = null
            viewModel = null
        }
    }

    LaunchedEffect(key1 = selectedDate) {
        coroutineScope.launch(Dispatchers.IO) {
            syncData(false)
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

    BlurredScreen(
        locked = true,
        onLocked = { },
        showLastweeksFastbreakCard.value,
        onDismiss = { showLastweeksFastbreakCard.value = false },
        date = null,
        hideLockCardButton = true,
        title = "Fastbreak Card Results",
        showCloseButton = true,
        fastbreakViewModel = viewModel,
        fastbreakResultsCard = true,
        onShowHelp = { openHelpSheet(HelpPage.FASTBREAK_RESULTS_CARD) },
        showHelpButton = true
    )
    BlurredScreen(
        locked,
        onLocked = { viewModel?.lockCard() },
        showModal.value,
        onDismiss = { showModal.value = false },
        date = selectedDate,
        fastbreakViewModel = viewModel,
        onShowHelp = { openHelpSheet(HelpPage.DAILY_FASTBREAK_CARD) },
        showHelpButton = true
    )
    ModalDrawer(
        modifier = Modifier.background(color = colors.background)
            .then(if (showModal.value) Modifier.blur(16.dp) else Modifier),
        drawerState = drawerState,
        drawerContent = {
//            ThemeSelector(themePreference = themePreference, onToggleTheme = onToggleTheme)
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
                },
                statSheetItems = state?.statSheetItems,
                lastFetchedDate = dailyFastbreak?.lastFetchedDate ?: 0,
                onSync = {
                    scope.launch(Dispatchers.IO) {
                        syncData(true)
                    }
                },
                username = authRepository.getUser()?.userName ?: "",
                onShowStatSheetHelp = { openHelpSheet(HelpPage.STAT_SHEET) }
            )
        }
    ) {
        HomeScaffold(
            component = component,
            scope = scope,
            drawerState = drawerState,
            onLogout = onLogout,
            locked = locked,
            listState = listState,
            animatedAlpha = animatedAlpha,
            showModal = showModal,
            dailyFastbreak = dailyFastbreak,
            viewModel = viewModel,
            scrollState = scrollState,
            selectedDate = selectedDate,
            authedUser = authRepository.getUser(),
            error = error,
            themePreference = themePreference,
            onToggleTheme = onToggleTheme,
            onSync = {
                scope.launch(Dispatchers.IO) {
                    syncData(true)
                }
            },
            showBottomSheet = showBottomSheet,
            onDismissBottomSheet = { showBottomSheet = false },
            onShowHelp = openHelpSheet
        )
        
        SimpleBottomSheetExample(
            showBottomSheet = showBottomSheet,
            onDismiss = { showBottomSheet = false },
            helpContent = HelpData.getHelpContent(bottomSheetHelpPage)
        )
    }
}