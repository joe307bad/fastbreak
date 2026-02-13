package com.joebad.fastbreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.Narrative
import com.joebad.fastbreak.data.model.TopicsResponse
import com.joebad.fastbreak.navigation.TopicsComponent
import com.joebad.fastbreak.platform.UrlLauncher
import kotlin.time.Clock
import kotlin.time.Instant

private fun formatRelativeTime(instant: Instant?): String {
    if (instant == null) return "never"
    val now = Clock.System.now()
    val diff = now - instant
    val minutes = diff.inWholeMinutes
    val hours = diff.inWholeHours
    val days = diff.inWholeDays
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}

@Composable
fun TopicsScreen(
    component: TopicsComponent,
    topics: TopicsResponse?,
    topicsUpdatedAt: Instant?,
    onMenuClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("topics") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = component.onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        if (topics == null || topics.narratives.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "no topics available",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        text = "updated ${formatRelativeTime(topicsUpdatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                itemsIndexed(topics.narratives) { index, narrative ->
                    NarrativeItem(index + 1, narrative)
                }
            }
        }
    }
}

private fun getLeagueColor(league: String): Color {
    return when (league.lowercase()) {
        "nba" -> Color(0xFFE31837)  // NBA red (vivid)
        "nfl" -> Color(0xFF0066CC)  // NFL blue (vivid)
        "nhl" -> Color(0xFFFC4C02)  // NHL orange (visible in dark mode)
        "mlb" -> Color(0xFF0052A5)  // MLB blue (vivid)
        "mls" -> Color(0xFF00B140)  // MLS green (vivid)
        else -> Color(0xFF757575)   // Gray for unknown
    }
}

@Composable
private fun NarrativeItem(number: Int, narrative: Narrative) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .padding(bottom = 40.dp)
    ) {
        // League badge above title
        if (narrative.league.isNotBlank()) {
            val leagueColor = getLeagueColor(narrative.league)
            Box(
                modifier = Modifier
                    .background(leagueColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = narrative.league.uppercase(),
                    color = leagueColor,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = "$number. ${narrative.title}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Summary
        if (narrative.summary.isNotBlank()) {
            Text(
                text = narrative.summary,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Statistical context prose
        if (narrative.statisticalContext.isNotBlank()) {
            Column(
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Green badge for "statistical context"
                val greenColor = Color(0xFF4CAF50)
                Box(
                    modifier = Modifier
                        .background(greenColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "statistical analysis",
                        color = greenColor,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = narrative.statisticalContext,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Data points (show up to 3)
        if (narrative.dataPoints.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Orange badge for "data points"
                val orangeColor = Color(0xFFFF9800)
                Box(
                    modifier = Modifier
                        .background(orangeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "data points",
                        color = orangeColor,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                narrative.dataPoints.take(3).forEach { dp ->
                    Row {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val teamPrefix = if (dp.team.isNotBlank()) "${dp.team}: " else ""
                        Text(
                            text = "$teamPrefix${dp.metric} = ${dp.value}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Links
        if (narrative.links.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                // Blue badge for "source links"
                val blueColor = Color(0xFF2196F3)
                Box(
                    modifier = Modifier
                        .background(blueColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "source links",
                        color = blueColor,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Links with spacing between them
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    narrative.links.forEach { link ->
                        Row(
                            modifier = Modifier.clickable { UrlLauncher.openUrl(link.url) }
                        ) {
                            Text(
                                text = "• ",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "[${link.type}] ${link.title}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.tertiary,
                                textDecoration = TextDecoration.Underline
                            )
                        }
                    }
                }
            }
        }
    }
}
