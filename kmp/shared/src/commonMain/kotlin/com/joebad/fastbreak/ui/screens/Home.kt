
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
                    .padding(vertical = 4.dp, horizontal = 8.dp)
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
            CacheStatusSection(appDataState, colors)
        }

        // Schedule Data - show all games
        appDataState.scheduleData?.let { schedule ->
            item {
                ScheduleSection(schedule.fastbreakCard, colors)
            }
        }

        // Stats Data  
        appDataState.statsData?.let { stats ->
            item {
                StatsSection(stats, colors)
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
        Text(
            text = "CACHE STATUS",
            style = MaterialTheme.typography.caption,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SCH:",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = when {
                    appDataState.scheduleIsRefreshing -> "REFRESH"
                    appDataState.scheduleIsStale -> "STALE"
                    appDataState.scheduleData != null -> "FRESH"
                    else -> "NONE"
                },
                style = MaterialTheme.typography.caption,
                color = colors.onSurface,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "STA:",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = when {
                    appDataState.statsIsRefreshing -> "REFRESH"
                    appDataState.statsIsStale -> "STALE"
                    appDataState.statsData != null -> "FRESH"
                    else -> "NONE"
                },
                style = MaterialTheme.typography.caption,
                color = colors.onSurface,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SRC:",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (appDataState.cacheStatus.isCached) "CACHE" else "NET",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Divider(
            color = colors.onSurface.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
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
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        
        games.forEach { game ->
            GameReceiptRow(game, colors)
        }
        
        Divider(
            color = colors.onSurface.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
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
        Text(
            text = game.dateLine1 ?: game.dateLine2 ?: "",
            style = MaterialTheme.typography.caption,
            color = colors.onSurface.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun StatsSection(statsData: com.joebad.fastbreak.model.dtos.StatsResponse, colors: AppColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "WEEKLY STATS",
            style = MaterialTheme.typography.caption,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "WEEK: ${statsData.weekStartDate}",
            style = MaterialTheme.typography.caption,
            color = colors.onSurface.copy(alpha = 0.7f),
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
                modifier = Modifier.padding(top = 4.dp)
            )

            leaderboard.dailyLeaderboards.forEachIndexed { index, dailyLeaderboard ->
                dailyLeaderboard.entries.forEachIndexed { index, leaderboardEntry ->
                    LeaderboardReceiptRow(index + 1, LeaderboardItem(leaderboardEntry.userId, leaderboardEntry.userName, leaderboardEntry.points), colors)
                }
            }
            Text(
                text = "WEEKLY TOTALS:",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )

            leaderboard.weeklyTotals.forEachIndexed { index, weeklyTotal ->
                LeaderboardReceiptRow(index + 1, LeaderboardItem(weeklyTotal.userId, weeklyTotal.userName, weeklyTotal.points), colors)
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
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                       "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
}