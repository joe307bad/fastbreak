
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.DrawerState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.ProtectedComponent
import com.joebad.fastbreak.Theme
import com.joebad.fastbreak.ThemePreference
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakViewModel
import com.joebad.fastbreak.model.dtos.DailyFastbreak
import com.joebad.fastbreak.ui.help.HelpData
import com.joebad.fastbreak.ui.help.HelpPage
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.QuestionmarkCircle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Composable
fun HomeScaffold(
    component: ProtectedComponent,
    scope: CoroutineScope,
    drawerState: DrawerState,
    onLogout: () -> Unit,
    locked: Boolean,
    listState: LazyListState,
    animatedAlpha: Float,
    showModal: MutableState<Boolean>,
    dailyFastbreak: DailyFastbreak?,
    viewModel: FastbreakViewModel?,
    scrollState: ScrollState,
    selectedDate: String,
    authedUser: AuthedUser?,
    error: String?,
    onSync: () -> Unit,
    themePreference: ThemePreference,
    onToggleTheme: (theme: Theme) -> Unit,
    showBottomSheet: Boolean,
    onDismissBottomSheet: () -> Unit,
    onShowHelp: (HelpPage) -> Unit
) {
    val colors = LocalColors.current;
    val childStack by component.stack.subscribeAsState()
    val activeChild = childStack.active
    val leaderboard =
        dailyFastbreak?.leaderboard?.dailyLeaderboards?.find { l -> l.dateCode == selectedDate }
    val state = viewModel?.container?.stateFlow?.collectAsState()?.value;
    
    val currentHelpPage = when (activeChild.instance) {
        is ProtectedComponent.Child.Home -> HelpPage.HOME
        is ProtectedComponent.Child.Leaderboard -> HelpPage.LEADERBOARD
        is ProtectedComponent.Child.Profile -> HelpPage.PROFILE
        is ProtectedComponent.Child.Settings -> HelpPage.SETTINGS
        else -> HelpPage.HOME
    }
    val helpContent = HelpData.getHelpContent(currentHelpPage)

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
                BottomNavigationItem(
                    icon = {
                        Icon(
                            Icons.Default.Person,
                            tint = colors.onPrimary,
                            contentDescription = "Profile"
                        )
                    },
                    label = { Text("Profile", color = colors.onPrimary) },
                    selected = activeChild::class == ProtectedComponent.Child.Profile::class,
                    onClick = { component.selectTab(ProtectedComponent.Config.Profile) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.padding(paddingValues).fillMaxWidth()
                .background(color = colors.background)
        ) {
            Children(
                stack = childStack,
            ) { child ->

                Column(modifier = Modifier.zIndex(2f)) {
                    when (child.instance) {
                        is ProtectedComponent.Child.Settings -> {
                            SettingsScreen(dailyFastbreak?.lastFetchedDate ?: 0, onSync, themePreference, onToggleTheme)
                        }

                        is ProtectedComponent.Child.Home -> {
                            HomeScreen(
                                locked,
                                listState,
                                animatedAlpha,
                                showModal,
                                dailyFastbreak,
                                viewModel,
                                selectedDate,
                                error
                            )
                        }

                        is ProtectedComponent.Child.Leaderboard -> {
                            LeaderboardScreen(scrollState, leaderboard)
                        }

                        is ProtectedComponent.Child.Profile -> {
                            authedUser?.let {
                                ProfileScreen(
                                    userId = authedUser.userId,
                                    email = authedUser.email,
                                    userName = authedUser.userName,
                                    loading = state?.isSavingUserName,
                                    onSaveUserName = { n -> viewModel?.saveUserName(n) },
                                    logout = { onLogout() }
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().zIndex(5f)) {
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
                    Spacer(modifier = Modifier.weight(1f))
                    SmallFloatingActionButton(
                        onClick = {
                            onShowHelp(currentHelpPage)
                        },
                        modifier = Modifier
                            .offset(x = 5.dp, y = 0.dp)
                            .padding(16.dp)
                            .zIndex(5f),
                        containerColor = colors.primary,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        CupertinoIcon(
                            imageVector = CupertinoIcons.Outlined.QuestionmarkCircle,
                            contentDescription = "How to play",
                            tint = colors.text,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}