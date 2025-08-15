
package com.joebad.fastbreak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.data.global.AppDataState
import com.joebad.fastbreak.model.dtos.LeaderboardItem
import com.joebad.fastbreak.ui.LockableButton
import com.joebad.fastbreak.ui.screens.schedule.ScheduleAction
import com.joebad.fastbreak.ui.screens.schedule.ScheduleSection
import com.joebad.fastbreak.ui.screens.schedule.ScheduleViewModel
import com.joebad.fastbreak.ui.theme.AppColors
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControl
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlDefaults
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlTab
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class HomeTab {
    PICKS, LEADERBOARD
}

@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun HomeScreen(appDataState: AppDataState, onLogout: () -> Unit = {}) {
    val colors = LocalColors.current
    val scheduleViewModel = remember { ScheduleViewModel() }
    val scheduleState by scheduleViewModel.container.stateFlow.collectAsState()
    var selectedTab by remember { mutableStateOf(HomeTab.PICKS) }
    
    val hasPicks = scheduleState.selectedWinners.isNotEmpty()
    val picksCount = scheduleState.selectedWinners.size

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(bottom = if (selectedTab == HomeTab.PICKS && hasPicks) 80.dp else 0.dp),
            verticalArrangement = Arrangement.Top
        ) {
        // Header with title and segmented control
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(vertical = 5.dp, horizontal = 8.dp)
            ) {
                Text(
                    text = "FASTBREAK",
                    style = MaterialTheme.typography.body1,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = getCurrentDateFormatted(),
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Segmented control for Picks/Leaderboard
                CupertinoSegmentedControl(
                    colors = CupertinoSegmentedControlDefaults.colors(
                        separatorColor = colors.accent,
                        indicatorColor = colors.accent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    selectedTabIndex = when (selectedTab) {
                        HomeTab.PICKS -> 0
                        HomeTab.LEADERBOARD -> 1
                    },
                    shape = RectangleShape
                ) {
                    CupertinoSegmentedControlTab(
                        isSelected = selectedTab == HomeTab.PICKS,
                        onClick = { selectedTab = HomeTab.PICKS }
                    ) {
                        Text(
                            "PICKS", 
                            color = if (selectedTab == HomeTab.PICKS) colors.onAccent else colors.text,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    CupertinoSegmentedControlTab(
                        isSelected = selectedTab == HomeTab.LEADERBOARD,
                        onClick = { selectedTab = HomeTab.LEADERBOARD }
                    ) {
                        Text(
                            "LEADERBOARD", 
                            color = if (selectedTab == HomeTab.LEADERBOARD) colors.onAccent else colors.text,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Divider(
                    color = colors.onSurface.copy(alpha = 0.3f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // Content based on selected tab
        if (selectedTab == HomeTab.PICKS) {
            // Schedule Data - show all games
            appDataState.scheduleData?.let { schedule ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.background)
                            .padding(vertical = 5.dp, horizontal = 8.dp)
                    ) {
                        ScheduleSection(schedule.fastbreakCard, colors, scheduleViewModel)
                    }
                }
            }
        }

        if (selectedTab == HomeTab.LEADERBOARD) {
            // Stats Data - Leaderboard view
            appDataState.statsData?.let { stats ->
                stats.weeklyLeaderboard?.let { leaderboard ->
                    // Daily Leaderboard (top half)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.background)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            DailyLeaderboardSection(leaderboard, colors)
                        }
                    }
                    
                    // Weekly Totals (bottom half)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.background)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            WeeklyTotalsSection(leaderboard, colors)
                        }
                    }
                }
            }
        }

        // Loading State
        if (appDataState.isLoading) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "LOADING...",
                        style = MaterialTheme.typography.caption,
                        color = colors.onSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Error State
        appDataState.cacheStatus.error?.let { error ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "ERROR:",
                        style = MaterialTheme.typography.caption,
                        color = colors.error,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.caption,
                        color = colors.error,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        }
        
        // Lockable Card pinned to bottom when picks are selected
        if (selectedTab == HomeTab.PICKS && hasPicks) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colors.background.copy(alpha = 0.95f))
                    .padding(16.dp)
            ) {
                LockableButton(
                    onClick = {
                        scheduleViewModel.handleAction(ScheduleAction.SubmitPicks)
                    },
                    onLock = {
                        scheduleViewModel.handleAction(ScheduleAction.LockPicks)
                    },
                    lockable = true,
                    isLocked = scheduleState.isLocked,
                    modifier = Modifier.fillMaxWidth().zIndex(1f)
                ) {
                    androidx.compose.material3.Text(
                        text = "SUBMIT $picksCount PICK${if (picksCount == 1) "" else "S"}",
                        color = LocalColors.current.onSecondary
                    )
                }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .offset(y = ButtonDefaults.MinHeight - 1.dp)
                                .background(colors.accent)
                        ) {
                            androidx.compose.material3.Text(text = " ")
                        }
            }
        }
    }
}



@Composable
private fun DailyLeaderboardSection(
    leaderboard: com.joebad.fastbreak.model.dtos.LeaderboardResult,
    colors: AppColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "DAILY LEADERBOARD",
            style = MaterialTheme.typography.caption,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Highest card results for each day",
            style = MaterialTheme.typography.caption,
            color = colors.onSurface.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(modifier = Modifier.padding(start = 16.dp)) {
            leaderboard.dailyLeaderboards.forEach { dailyLeaderboard ->
                if (dailyLeaderboard.entries.isNotEmpty()) {
                    Text(
                        text = dailyLeaderboard.dateCode,
                        style = MaterialTheme.typography.caption,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                    dailyLeaderboard.entries.take(3).forEachIndexed { index, leaderboardEntry ->
                        LeaderboardReceiptRow(
                            position = index + 1,
                            item = LeaderboardItem(
                                id = leaderboardEntry.userId,
                                user = leaderboardEntry.userName,
                                points = leaderboardEntry.points
                            ),
                            colors = colors
                        )
                    }
                }
            }
        }
        
        Divider(
            color = colors.onSurface.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun WeeklyTotalsSection(
    leaderboard: com.joebad.fastbreak.model.dtos.LeaderboardResult,
    colors: AppColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "WEEKLY TOTALS",
            style = MaterialTheme.typography.caption,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Running total for the week",
            style = MaterialTheme.typography.caption,
            color = colors.onSurface.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        leaderboard.weeklyTotals.forEachIndexed { index, weeklyTotal ->
            LeaderboardReceiptRow(
                position = index + 1,
                item = LeaderboardItem(
                    id = weeklyTotal.userId,
                    user = weeklyTotal.userName,
                    points = weeklyTotal.points
                ),
                colors = colors
            )
        }
        
        Divider(
            color = colors.onSurface.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun LeaderboardReceiptRow(position: Int, item: LeaderboardItem, colors: AppColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$position. ${item.user}",
            style = MaterialTheme.typography.caption,
            color = colors.onSurface,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${item.points}pts",
            style = MaterialTheme.typography.caption,
            color = colors.onSurface,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun getCurrentDateFormatted(): String {
    val etTimeZone = TimeZone.of("America/New_York")
    val nowET = Clock.System.now().toLocalDateTime(etTimeZone)
    val date = nowET.date
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    return "${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
}

private fun formatExpirationTime(expiresAt: Instant?): String {
    return expiresAt?.let { instant ->
        val etTimeZone = TimeZone.of("America/New_York")
        val expirationET = instant.toLocalDateTime(etTimeZone)
        val time = expirationET.time
        val hour = if (time.hour == 0) 12 else if (time.hour > 12) time.hour - 12 else time.hour
        val amPm = if (time.hour < 12) "AM" else "PM"
        val minute = time.minute.toString().padStart(2, '0')
        "$hour:$minute $amPm"
    } ?: ""
}

private fun formatLastFetchedTime(lastFetchedAt: Instant?): String {
    return lastFetchedAt?.let { instant ->
        val etTimeZone = TimeZone.of("America/New_York")
        val fetchedET = instant.toLocalDateTime(etTimeZone)
        val time = fetchedET.time
        val hour = if (time.hour == 0) 12 else if (time.hour > 12) time.hour - 12 else time.hour
        val amPm = if (time.hour < 12) "AM" else "PM"
        val minute = time.minute.toString().padStart(2, '0')
        "$hour:$minute $amPm"
    } ?: ""
}