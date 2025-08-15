import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.global.AppDataState
import com.joebad.fastbreak.model.dtos.EmptyFastbreakCardItem
import com.joebad.fastbreak.model.dtos.LeaderboardItem
import com.joebad.fastbreak.ui.theme.AppColors
import com.joebad.fastbreak.ui.theme.LocalColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HomeScreen(appDataState: AppDataState) {
    val colors = LocalColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.Top
    ) {
        // Simple header line
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
                Divider(
                    color = colors.onSurface.copy(alpha = 0.3f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // Cache Status - compact
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(vertical = 5.dp, horizontal = 8.dp)
            ) {
                CacheStatusSection(appDataState, colors)
            }
        }

        // Schedule Data - show all games
        appDataState.scheduleData?.let { schedule ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background)
                        .padding(vertical = 5.dp, horizontal = 8.dp)
                ) {
                    ScheduleSection(schedule.fastbreakCard, colors)
                }
            }
        }

        // Stats Data  
        appDataState.statsData?.let { stats ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background)
                        .padding(vertical = 5.dp, horizontal = 8.dp)
                ) {
                    StatsSection(stats, colors)
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
}

@Composable
private fun CacheStatusSection(appDataState: AppDataState, colors: AppColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "/day/${appDataState.dateCode}/schedule",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.End
            ) {
                Text(
                    text = when {
                        appDataState.scheduleIsRefreshing -> "REFRESHING"
                        appDataState.scheduleIsStale -> "STALE"
                        appDataState.scheduleData != null && appDataState.scheduleIsFromCache -> "CACHED/${
                            formatExpirationTime(
                                appDataState.scheduleExpiresAt
                            )
                        }"

                        appDataState.scheduleData != null -> "FRESH"
                        else -> "NONE"
                    },
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "/day/${appDataState.dateCode}/stats/${appDataState.statsData?.statSheetForUser?.userId}",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.End
            ) {
                Text(
                    text = when {
                        appDataState.statsIsRefreshing -> "REFRESHING"
                        appDataState.statsIsStale -> "STALE"
                        appDataState.statsData != null && appDataState.statsIsFromCache -> "CACHED/${
                            formatExpirationTime(
                                appDataState.statsExpiresAt
                            )
                        }"

                        appDataState.statsData != null -> "FRESH"
                        else -> "NONE"
                    },
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

    }
}

@Composable
private fun ScheduleSection(games: List<EmptyFastbreakCardItem>, colors: AppColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "TODAY'S SCHEDULE (${games.size})",
            style = MaterialTheme.typography.caption,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        games.forEach { game ->
            GameReceiptRow(game, colors)
        }

    }
}

@Composable
private fun GameReceiptRow(game: EmptyFastbreakCardItem, colors: AppColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (game.awayTeam != null && game.homeTeam != null) {
                "${game.awayTeam} @ ${game.homeTeam}"
            } else {
                game.type
            },
            style = MaterialTheme.typography.caption,
            color = colors.onSurface,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatsSection(
    statsData: com.joebad.fastbreak.model.dtos.StatsResponse,
    colors: AppColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "WEEKLY STATS / ${statsData.weekStartDate} ",
            style = MaterialTheme.typography.caption,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        // Leaderboard
        statsData.weeklyLeaderboard?.let { leaderboard ->
            Text(
                text = "DAILY LEADERBOARD:",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = "Highest card results for each day",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Column(modifier = Modifier.padding(start = 20.dp)) {
                leaderboard.dailyLeaderboards.forEachIndexed { index, dailyLeaderboard ->
                    dailyLeaderboard.entries.isNotEmpty().let { hasItems ->
                        if (hasItems) {
                            Text(
                                text = dailyLeaderboard.dateCode,
                                style = MaterialTheme.typography.caption,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            dailyLeaderboard.entries.forEachIndexed { index, leaderboardEntry ->
                                LeaderboardReceiptRow(
                                    index + 1,
                                    LeaderboardItem(
                                        leaderboardEntry.userId,
                                        leaderboardEntry.userName,
                                        leaderboardEntry.points
                                    ),
                                    colors
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = "WEEKLY TOTALS:",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                text = "Running total for the week",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            leaderboard.weeklyTotals.forEachIndexed { index, weeklyTotal ->
                LeaderboardReceiptRow(
                    index + 1,
                    LeaderboardItem(weeklyTotal.userId, weeklyTotal.userName, weeklyTotal.points),
                    colors
                )
            }

        }

//
//        statsData.statSheetForUser?.items?.let { items ->
//            Text(
//                text = "STAT SHEET:",
//                style = MaterialTheme.typography.caption,
//                color = colors.onSurface,
//                fontWeight = FontWeight.Bold,
//                fontFamily = FontFamily.Monospace,
//                modifier = Modifier.padding(top = 4.dp)
//            )
//
//            items.forEachIndexed { index, items ->
//                dailyLeaderboard.entries.forEachIndexed { index, leaderboardEntry ->
//                    LeaderboardReceiptRow(index + 1, LeaderboardItem(leaderboardEntry.userId, leaderboardEntry.userName, leaderboardEntry.points), colors)
//                }
//            }

//
//        }

        Divider(
            color = colors.onSurface.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
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