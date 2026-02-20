package com.joebad.fastbreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import com.joebad.fastbreak.data.model.Narrative
import com.joebad.fastbreak.data.model.NarrativeDataPoint
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.TextSegment
import com.joebad.fastbreak.data.model.TopicsResponse
import com.joebad.fastbreak.data.model.VizType
import com.joebad.fastbreak.navigation.TopicsComponent
import com.joebad.fastbreak.ui.components.InfoBottomSheet
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    component: TopicsComponent,
    topics: TopicsResponse?,
    topicsUpdatedAt: Instant?,
    onMenuClick: () -> Unit = {}
) {
    var showInfoSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Load persisted collapsed and read indices on first composition
    val initialCollapsedIndices = remember { component.getCollapsedIndices() }
    val initialReadIndices = remember { component.getReadIndices() }

    val collapsedIndices = remember { mutableStateListOf<Int>().apply { addAll(initialCollapsedIndices) } }
    val readIndices = remember { mutableStateListOf<Int>().apply { addAll(initialReadIndices) } }

    // Save collapsed indices whenever they change
    LaunchedEffect(collapsedIndices.toList()) {
        component.saveCollapsedIndices(collapsedIndices.toSet())
    }

    // Save read indices whenever they change
    LaunchedEffect(readIndices.toList()) {
        component.saveReadIndices(readIndices.toSet())
    }

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
                    if (topics?.descriptionSegments?.isNotEmpty() == true || topics?.description?.isNotBlank() == true) {
                        IconButton(onClick = { showInfoSheet = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Topics Info")
                        }
                    }
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
                state = listState,
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    val totalNarratives = topics.narratives.size
                    val readCount = readIndices.size
                    Text(
                        text = "Daily at 10am ET - updated ${formatRelativeTime(topicsUpdatedAt)} - $readCount/$totalNarratives read",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                itemsIndexed(topics.narratives) { index, narrative ->
                    NarrativeItem(
                        number = index + 1,
                        narrative = narrative,
                        isCollapsed = index in collapsedIndices,
                        onToggleCollapse = {
                            if (index in collapsedIndices) {
                                collapsedIndices.remove(index)
                            } else {
                                collapsedIndices.add(index)
                                // Mark as read when collapsed
                                if (index !in readIndices) {
                                    readIndices.add(index)
                                }
                            }
                        },
                        onCollapseFromBottom = {
                            collapsedIndices.add(index)
                            // Mark as read when collapsed
                            if (index !in readIndices) {
                                readIndices.add(index)
                            }
                            // Scroll to next item (index + 2 because of header item at index 0)
                            val nextItemIndex = index + 2
                            coroutineScope.launch {
                                listState.scrollToItem(nextItemIndex)
                            }
                        },
                        onNavigateToChart = component.onNavigateToChart
                    )
                }
            }
        }
    }

    // Info bottom sheet
    if (showInfoSheet && (topics?.descriptionSegments?.isNotEmpty() == true || topics?.description?.isNotBlank() == true)) {
        InfoBottomSheet(
            onDismiss = { showInfoSheet = false },
            title = "about topics"
        ) {
            if (topics?.descriptionSegments?.isNotEmpty() == true) {
                SegmentedText(
                    segments = topics.descriptionSegments,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    accentColor = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            } else if (topics?.description?.isNotBlank() == true) {
                Text(
                    text = topics.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getLeagueColor(league: String): Color {
    return when (league.lowercase()) {
        "nba" -> Color(0xFFE31837)  // NBA red (vivid)
        "nfl" -> Color(0xFF00B140)  // NFL green
        "nhl" -> Color(0xFFFC4C02)  // NHL orange (visible in dark mode)
        "mlb" -> Color(0xFF0052A5)  // MLB blue (vivid)
        "mls" -> Color(0xFF00B140)  // MLS green (vivid)
        "cbb" -> Color(0xFF9C27B0)  // CBB purple (college basketball)
        else -> Color(0xFF757575)   // Gray for unknown
    }
}

@Composable
private fun SegmentedText(
    segments: List<TextSegment>,
    textColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 18.sp
) {
    val linkIconColor = MaterialTheme.colorScheme.onBackground
    val linkIconSize = fontSize * 0.9f

    val annotatedString = buildAnnotatedString {
        segments.forEach { segment ->
            when (segment.type) {
                "link" -> {
                    val startIndex = length
                    pushStringAnnotation(tag = "URL", annotation = segment.url ?: "")
                    withStyle(SpanStyle(color = textColor, textDecoration = TextDecoration.Underline)) {
                        append(segment.value)
                    }
                    // Include the icon in the clickable area
                    withStyle(SpanStyle(color = linkIconColor, fontSize = linkIconSize)) {
                        append(" ↗")
                    }
                    pop()
                }
                else -> {
                    withStyle(SpanStyle(color = textColor)) {
                        append(segment.value)
                    }
                }
            }
        }
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            lineHeight = lineHeight
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    UrlLauncher.openUrl(annotation.item)
                }
        }
    )
}

/**
 * Maps a league string to Sport enum.
 */
private fun leagueToSport(league: String): Sport? {
    return when (league.uppercase()) {
        "NBA" -> Sport.NBA
        "NFL" -> Sport.NFL
        "NHL" -> Sport.NHL
        "MLB" -> Sport.MLB
        "CBB" -> Sport.CBB
        else -> null
    }
}

/**
 * Maps a vizType string to VizType enum.
 */
private fun stringToVizType(vizType: String): VizType? {
    return try {
        VizType.valueOf(vizType.uppercase())
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Builds a filters map from a data point's team/player values.
 */
private fun buildFiltersFromDataPoint(dataPoint: NarrativeDataPoint): Map<String, String>? {
    val filters = mutableMapOf<String, String>()
    if (dataPoint.team.isNotBlank()) {
        filters["team"] = dataPoint.team
    }
    // Player filter could be added here if needed in the future
    return filters.takeIf { it.isNotEmpty() }
}

@Composable
private fun NarrativeItem(
    number: Int,
    narrative: Narrative,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onCollapseFromBottom: () -> Unit,
    onNavigateToChart: (chartId: String, sport: Sport, vizType: VizType, filters: Map<String, String>?) -> Unit = { _, _, _, _ -> }
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .padding(bottom = if (isCollapsed) 0.dp else 40.dp)
    ) {
        if (isCollapsed) {
            // Collapsed view: single line with caret, title, and league badge on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCollapse() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "$number. ${narrative.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (narrative.league.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
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
                }
            }
        } else {
            // Expanded view: original layout with collapse caret
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
            Row(
                modifier = Modifier.clickable { onToggleCollapse() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "$number. ${narrative.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Summary
            if (narrative.summarySegments.isNotEmpty()) {
                SegmentedText(
                    segments = narrative.summarySegments,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else if (narrative.summary.isNotBlank()) {
                Text(
                    text = narrative.summary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Statistical context prose
            if (narrative.statisticalContextSegments.isNotEmpty() || narrative.statisticalContext.isNotBlank()) {
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
                    if (narrative.statisticalContextSegments.isNotEmpty()) {
                        SegmentedText(
                            segments = narrative.statisticalContextSegments,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = narrative.statisticalContext,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Data points (show first 5)
            if (narrative.dataPoints.isNotEmpty()) {
                val displayedDataPoints = narrative.dataPoints.take(5)
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
                    displayedDataPoints.forEach { dp ->
                        val sport = leagueToSport(narrative.league)
                        val vizType = stringToVizType(dp.vizType)
                        val isClickable = dp.id.isNotBlank() && sport != null && vizType != null
                        val filters = buildFiltersFromDataPoint(dp)

                        Column {
                            Row(
                                modifier = if (isClickable) {
                                    Modifier.clickable {
                                        onNavigateToChart(
                                            dp.id,
                                            sport!!,
                                            vizType!!,
                                            filters
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val teamPrefix = if (dp.team.isNotBlank()) "${dp.team}: " else ""
                                val displayValue = dp.value.toIntOrNull()?.let { kotlin.math.abs(it).toString() } ?: dp.value
                                Text(
                                    text = "$teamPrefix${dp.metric} = $displayValue",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (isClickable) TextDecoration.Underline else TextDecoration.None
                                )
                                if (isClickable) {
                                    Text(
                                        text = " →",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            // Chart ID below the stat
                            if (dp.id.isNotBlank()) {
                                Text(
                                    text = "  [${dp.id}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
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
                                modifier = Modifier.clickable { UrlLauncher.openUrl(link.url) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "[${link.type}] ${link.title}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textDecoration = TextDecoration.Underline
                                )
                                Text(
                                    text = " ↗",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }

            // "Mark as read" text button at bottom right
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "[mark as read]",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onCollapseFromBottom() }
                )
            }
        }
    }
}
