package com.joebad.fastbreak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.global.AppDataState
import com.joebad.fastbreak.ui.PhysicalButton
import com.joebad.fastbreak.ui.theme.AppColors
import com.joebad.fastbreak.ui.theme.LocalColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ProfileScreen(appDataState: AppDataState, onLogout: () -> Unit = {}) {
    val colors = LocalColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.Top
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(vertical = 5.dp, horizontal = 8.dp)
            ) {
                Text(
                    text = "PROFILE",
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

        // Cache Status Section
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

        // Logout button
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PhysicalButton(
                    onClick = onLogout,
                    modifier = Modifier.width(120.dp),
                    backgroundColor = colors.error,
                    contentColor = colors.onError,
                    bottomBorderColor = colors.error.copy(alpha = 0.7f),
                    elevation = 6.dp,
                    pressDepth = 3.dp
                ) {
                    Text(
                        text = "LOGOUT",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
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
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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
                        }/${formatLastFetchedTime(appDataState.scheduleCachedAt)}"

                        appDataState.scheduleData != null -> "FRESH/${formatLastFetchedTime(appDataState.scheduleCachedAt)}"
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
                        }/${formatLastFetchedTime(appDataState.statsCachedAt)}"

                        appDataState.statsData != null -> "FRESH/${formatLastFetchedTime(appDataState.statsCachedAt)}"
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