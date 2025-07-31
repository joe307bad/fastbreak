
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.DrawerValue
import androidx.compose.material.ModalDrawer
import androidx.compose.material.rememberDrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appstractive.jwt.JWT
import com.appstractive.jwt.from
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.ProtectedComponent
import com.joebad.fastbreak.Theme
import com.joebad.fastbreak.ThemePreference
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakStateRepository
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakViewModel
import com.joebad.fastbreak.model.dtos.DailyFastbreak
import com.joebad.fastbreak.ui.GoogleSignInButton
import com.joebad.fastbreak.ui.PhysicalButton
import com.joebad.fastbreak.ui.SimpleBottomSheetExample
import com.joebad.fastbreak.ui.Title
import com.joebad.fastbreak.ui.help.HelpData
import com.joebad.fastbreak.ui.help.HelpPage
import com.joebad.fastbreak.ui.theme.LocalColors
import kotbase.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
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
    var showSigninBottomSheet by remember { mutableStateOf(false) }

    val openHelpSheet = { helpPage: HelpPage ->
        bottomSheetHelpPage = helpPage
        showBottomSheet = true
    }

//    val selectedDate = "20250728"
    val selectedDate = Clock.System.now().toLocalDateTime(TimeZone.of("America/New_York")).let {
        if (it.hour >= 5) it.date else it.date.minus(
            1,
            DateTimeUnit.DAY
        )
    }.format(
        LocalDate.Format { year(); monthNumber(); dayOfMonth() })

    val db = remember { Database("fastbreak") }
    val dailyFastbreakRepository =
        remember { FastbreakStateRepository(db, authRepository) }

    val syncData: suspend (forceUpdate: Boolean) -> Unit = { forceUpdate ->
        try {
            error = null // Clear previous errors
            val state = dailyFastbreakRepository.getDailyFastbreakState(selectedDate, forceUpdate)
            dailyFastbreak = state
            val statSheetItems = state?.statSheet?.items
            viewModel = FastbreakViewModel(
                db,
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
                e.message?.contains(
                    "network",
                    ignoreCase = true
                ) == true -> "Network connection failed. Please check your internet connection."

                e.message?.contains(
                    "timeout",
                    ignoreCase = true
                ) == true -> "Request timed out. Please try again."

                e.message?.contains(
                    "unauthorized",
                    ignoreCase = true
                ) == true -> "Authentication failed. Please log in again."

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

    // Listen for side effects from the ViewModel
    LaunchedEffect(viewModel) {
        viewModel?.container?.sideEffectFlow?.collect { sideEffect ->
            when (sideEffect) {
                is com.joebad.fastbreak.data.dailyFastbreak.FastbreakSideEffect.ShowSigninBottomSheet -> {
                    showSigninBottomSheet = true
                }
                is com.joebad.fastbreak.data.dailyFastbreak.FastbreakSideEffect.CardLocked -> {
                    // Handle card lock side effect - this triggers the onLock callback
                    coroutineScope.launch {
                        try {
                            val result = dailyFastbreakRepository.lockCardApi(sideEffect.state)
                            when (result) {
                                is com.joebad.fastbreak.data.dailyFastbreak.LockCardResult.Success -> {
                                    println("Card locked successfully: ${result.response.id}")
                                    viewModel?.completeCardLock()
                                }
                                is com.joebad.fastbreak.data.dailyFastbreak.LockCardResult.AuthenticationRequired -> {
                                    println("Authentication required, showing signin bottom sheet")
                                    viewModel?.unlockCard()
                                    viewModel?.showSigninBottomSheet()
                                }
                                is com.joebad.fastbreak.data.dailyFastbreak.LockCardResult.Error -> {
                                    println("Error locking card: ${result.message}")
                                    viewModel?.unlockCard()
                                }
                            }
                        } catch (e: Exception) {
                            println("Exception locking card: ${e.message}")
                            viewModel?.unlockCard()
                        }
                    }
                }
                else -> {}
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
        showHelpButton = true,
        showCloseButton = true,
        isLoading =  state?.isLocking
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

        SigninBottomSheet(
            showBottomSheet = showSigninBottomSheet,
            onDismiss = { showSigninBottomSheet = false },
            authRepository = authRepository,
            onSigninSuccess = { 
                showSigninBottomSheet = false
                // Retry locking the card
                viewModel?.lockCard()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigninBottomSheet(
    showBottomSheet: Boolean,
    onDismiss: () -> Unit,
    authRepository: AuthRepository,
    onSigninSuccess: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val colors = LocalColors.current

    if (showBottomSheet) {
        ModalBottomSheet(
            containerColor = colors.background,
            onDismissRequest = onDismiss,
            sheetState = bottomSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Title("Sign In Required")

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Your session has expired. Please sign in again to continue.",
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )
                Box(modifier = Modifier.height(100.dp)) {
                    GoogleSignInButton(
                        onLogin = { token ->
                            if (token != null) {
                                val jwt = JWT.from(token)
                                val email = jwt.claims["email"].toString()
                                val exp = jwt.claims.get("exp").toString().toLong()
                                val sub = jwt.claims["sub"].toString().replace("\"", "")

                                val authedUser = AuthedUser(
                                    email,
                                    exp,
                                    token,
                                    userId = sub,
                                    userName = authRepository.getUser()?.userName ?: ""
                                )
                                authRepository.storeAuthedUser(authedUser)
                                onSigninSuccess()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                PhysicalButton(
                    borderColor = colors.accent,
                    backgroundColor = colors.background,
                    onClick = {
                        scope.launch {
                            bottomSheetState.hide()
                            onDismiss()
                        }
                    }
                ) {
                    Text("CLOSE", color = colors.text)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}