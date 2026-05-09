package com.joebad.fastbreak.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.TextSegment
import com.joebad.fastbreak.data.model.Topic
import com.joebad.fastbreak.data.model.TopicDataPoint
import com.joebad.fastbreak.data.model.TopicsResponse
import com.joebad.fastbreak.data.model.VizType
import com.joebad.fastbreak.navigation.TopicsV2Component
import com.joebad.fastbreak.platform.UrlLauncher
import com.joebad.fastbreak.ui.components.InfoBottomSheet
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch

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

private fun leagueToSport(league: String): Sport? = when (league.uppercase()) {
    "NBA" -> Sport.NBA
    "NFL" -> Sport.NFL
    "NHL" -> Sport.NHL
    "MLB" -> Sport.MLB
    "CBB" -> Sport.CBB
    else -> null
}

private fun stringToVizType(vizType: String): VizType? =
    if (vizType.isBlank()) null
    else try {
        VizType.valueOf(vizType.uppercase())
    } catch (_: IllegalArgumentException) {
        null
    }

// Returns the playoff bracket deep-link target for a topic, when one exists.
// Currently NBA and NHL ship a playoff bracket chart; MLB / NFL / CBB do not.
private data class BracketTarget(val chartId: String, val sport: Sport, val vizType: VizType)

private fun bracketTargetFor(league: String, category: String): BracketTarget? {
    if (!category.equals("PLAYOFFS", ignoreCase = true)) return null
    return when (league.uppercase()) {
        "NBA" -> BracketTarget("nba__playoff_bracket", Sport.NBA, VizType.NBA_PLAYOFF_BRACKET)
        "NHL" -> BracketTarget("nhl__playoff_bracket", Sport.NHL, VizType.NHL_PLAYOFF_BRACKET)
        else -> null
    }
}

// Truncate a numeric stat string to at most two decimal places without rounding.
// Non-numeric values (e.g. "true") and integer-formatted values pass through unchanged.
private fun truncateToTwoDecimals(value: String): String {
    if (!value.contains('.')) return value
    val parts = value.split('.', limit = 2)
    val decimals = parts[1].take(2)
    return if (decimals.isEmpty()) parts[0] else "${parts[0]}.$decimals"
}

private fun buildFiltersForDataPoint(dp: TopicDataPoint): Map<String, String>? {
    val filters = mutableMapOf<String, String>()
    when (dp.subjectType.lowercase()) {
        "team" -> if (dp.subject.isNotBlank()) filters["team"] = dp.subject
        "player" -> if (dp.subject.isNotBlank()) filters["player"] = dp.subject
    }
    return filters.takeIf { it.isNotEmpty() }
}

@Composable
private fun SwipeToDismissItem(
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    var width by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val w = width
                            if (w == 0) return@detectHorizontalDragGestures
                            val threshold = w * 0.4f
                            scope.launch {
                                if (abs(offsetX.value) > threshold) {
                                    val target = if (offsetX.value > 0) w.toFloat() else -w.toFloat()
                                    offsetX.animateTo(target, tween(200))
                                    onDismissed()
                                } else {
                                    offsetX.animateTo(0f, tween(200))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                }
        ) {
            content()
        }
    }
}

@Composable
private fun TopicLabel(
    league: String,
    category: String,
    fontSize: Float,
    modifier: Modifier = Modifier,
    onBracketClick: (() -> Unit)? = null
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val size = (fontSize * 0.6f).sp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = league.uppercase(),
            fontSize = size,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = accentColor
        )
        if (category.isNotBlank()) {
            Text(
                text = "  •  ",
                fontSize = size,
                fontFamily = FontFamily.Monospace,
                color = labelColor
            )
            Text(
                text = category.uppercase(),
                fontSize = size,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = labelColor
            )
        }
        if (onBracketClick != null) {
            Text(
                text = "  •  ",
                fontSize = size,
                fontFamily = FontFamily.Monospace,
                color = labelColor
            )
            Text(
                text = "BRACKET",
                fontSize = size,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = accentColor,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onBracketClick)
            )
        }
    }
}

@Composable
private fun SegmentedSummary(
    segments: List<TextSegment>,
    fallbackSummary: String,
    fontSize: Float,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground

    // Fall back to plain summary text if no segments are present.
    if (segments.isEmpty()) {
        Text(
            text = fallbackSummary,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.4f).sp,
            color = textColor,
            modifier = modifier
        )
        return
    }

    val annotated = buildAnnotatedString {
        segments.forEach { seg ->
            when (seg.type) {
                "link" -> {
                    val url = seg.url.orEmpty()
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ) { append(seg.value) }
                    pop()
                }
                else -> append(seg.value)
            }
        }
    }

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = androidx.compose.ui.text.TextStyle(
            color = textColor,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.4f).sp
        ),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { ann ->
                    if (ann.item.isNotBlank()) UrlLauncher.openUrl(ann.item)
                }
        }
    )
}

@Composable
private fun DataPointsSection(
    league: String,
    points: List<TopicDataPoint>,
    onNavigateToChart: (chartId: String, sport: Sport, vizType: VizType, filters: Map<String, String>?) -> Unit,
    fontSize: Float,
    modifier: Modifier = Modifier
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onBackground
    val accentColor = MaterialTheme.colorScheme.primary
    val headerSize = (fontSize * 0.6f).sp
    val rowSize = (fontSize * 0.72f).sp
    val sport = leagueToSport(league)

    Column(modifier = modifier) {
        Text(
            text = "DATA POINTS",
            fontSize = headerSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = labelColor,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        points.forEach { dp ->
            val vizType = stringToVizType(dp.vizType)
            val filters = buildFiltersForDataPoint(dp)
            val isClickable = sport != null && vizType != null && dp.source.isNotBlank()
            val rowModifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .let {
                    if (isClickable) {
                        it.clickable {
                            onNavigateToChart(dp.source, sport!!, vizType!!, filters)
                        }
                    } else it
                }
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dp.subject,
                    fontSize = rowSize,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Fixed fraction of the row width so the middle column starts at
                    // the same x-offset on every row, regardless of how wide
                    // the right-aligned value column is. Player rows get a wider
                    // subject column than team rows because full player names need
                    // more room than 3-letter team abbrevs.
                    modifier = Modifier.fillMaxWidth(
                        if (dp.subjectType.equals("player", ignoreCase = true)) 0.55f else 0.2f
                    )
                )
                Text(
                    text = dp.name,
                    fontSize = rowSize,
                    fontFamily = FontFamily.Monospace,
                    color = labelColor,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    text = truncateToTwoDecimals(dp.value) + (dp.rank?.let { " (#${it})" } ?: ""),
                    fontSize = rowSize,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isClickable) accentColor else valueColor,
                    textDecoration = if (isClickable) TextDecoration.Underline else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsV2Screen(
    component: TopicsV2Component,
    topics: TopicsResponse?,
    onMarkTopicsAsViewed: () -> Unit = {}
) {
    // Mark topics as viewed when the screen is displayed
    LaunchedEffect(Unit) {
        onMarkTopicsAsViewed()
    }

    val initial = remember(topics) { topics?.topics ?: emptyList() }
    val items = remember(initial) { mutableStateListOf<Topic>().apply { addAll(initial) } }
    var headerVisible by remember { mutableStateOf(true) }
    var showSizeSlider by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    // Restore the persisted font size on entry; default to 18sp if none has been saved yet.
    var fontSize by remember { mutableStateOf(component.getFontSize() ?: 18f) }
    val updatedAt = remember(topics) { component.getUpdatedAt() }
    val hasInfo = (topics?.infoSegments?.isNotEmpty() == true) || (topics?.info?.isNotBlank() == true)
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (headerVisible) {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Updated ${formatRelativeTime(updatedAt)}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = component.onNavigateBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                if (hasInfo) {
                                    IconButton(onClick = { showInfoSheet = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = "About topics"
                                        )
                                    }
                                }
                                TextButton(onClick = { showSizeSlider = !showSizeSlider }) {
                                    Text(
                                        text = "Aa",
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                IconButton(onClick = { headerVisible = false }) {
                                    Icon(
                                        imageVector = Icons.Default.ExpandLess,
                                        contentDescription = "Hide header"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                                actionIconContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )

                        AnimatedVisibility(visible = showSizeSlider) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "A",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 12.sp
                                )
                                Slider(
                                    value = fontSize,
                                    onValueChange = { fontSize = it },
                                    onValueChangeFinished = { component.saveFontSize(fontSize) },
                                    valueRange = 12f..32f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                                Text(
                                    text = "A",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 28.sp
                                )
                            }
                        }
                    }
                }

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "no topics available",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().then(
                            if (!headerVisible) Modifier.padding(top = 16.dp) else Modifier
                        )
                    ) {
                        items(
                            items = items,
                            key = { it.summary.take(40) + ":" + it.league }
                        ) { item ->
                            SwipeToDismissItem(
                                onDismissed = { items.remove(item) },
                                modifier = Modifier.animateItem()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 60.dp, top = 16.dp, bottom = 16.dp)
                                ) {
                                    val bracketTarget = remember(item.league, item.category) {
                                        bracketTargetFor(item.league, item.category)
                                    }
                                    TopicLabel(
                                        league = item.league,
                                        category = item.category,
                                        fontSize = fontSize,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                        onBracketClick = bracketTarget?.let { target ->
                                            {
                                                component.onNavigateToChart(
                                                    target.chartId,
                                                    target.sport,
                                                    target.vizType,
                                                    null
                                                )
                                            }
                                        }
                                    )
                                    SegmentedSummary(
                                        segments = item.summarySegments,
                                        fallbackSummary = item.summary,
                                        fontSize = fontSize,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (item.dataPoints.isNotEmpty()) {
                                        DataPointsSection(
                                            league = item.league,
                                            points = item.dataPoints,
                                            onNavigateToChart = component.onNavigateToChart,
                                            fontSize = fontSize,
                                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!headerVisible) {
                SmallFloatingActionButton(
                    onClick = { headerVisible = true },
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 32.dp)
                        .size(36.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Show header",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            SmallFloatingActionButton(
                onClick = {
                    val target = listState.firstVisibleItemIndex + 1
                    if (target < items.size) {
                        scrollScope.launch {
                            listState.animateScrollToItem(target)
                        }
                    }
                },
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 24.dp)
                    .size(36.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Next item",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showInfoSheet && hasInfo && topics != null) {
        InfoBottomSheet(
            onDismiss = { showInfoSheet = false },
            title = "about topics"
        ) {
            // Match the styling used by other chart info bottom sheets
            // (DataVizScreen / FilterBar): bodyMedium typography on onSurfaceVariant.
            val bodyStyle = MaterialTheme.typography.bodyMedium
            val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
            val linkColor = MaterialTheme.colorScheme.primary

            if (topics.infoSegments.isNotEmpty()) {
                val annotated = buildAnnotatedString {
                    topics.infoSegments.forEach { seg ->
                        when (seg.type) {
                            "link" -> {
                                val url = seg.url.orEmpty()
                                pushStringAnnotation(tag = "URL", annotation = url)
                                withStyle(
                                    SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline
                                    )
                                ) { append(seg.value) }
                                pop()
                            }
                            else -> append(seg.value)
                        }
                    }
                }
                ClickableText(
                    text = annotated,
                    style = bodyStyle.copy(color = bodyColor),
                    onClick = { offset ->
                        annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { ann ->
                                if (ann.item.isNotBlank()) UrlLauncher.openUrl(ann.item)
                            }
                    }
                )
            } else {
                Text(
                    text = topics.info,
                    style = bodyStyle,
                    color = bodyColor
                )
            }
        }
    }
}
