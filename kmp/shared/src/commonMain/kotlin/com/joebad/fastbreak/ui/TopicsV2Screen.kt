package com.joebad.fastbreak.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.navigation.TopicsV2Component
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private sealed interface Segment {
    data class Text(val text: String) : Segment
    data class Link(val text: String) : Segment
}

private data class StatRow(val subject: String, val stat: String, val value: String)

private data class TopicItem(
    val id: Int,
    val league: String,
    val category: String,
    val segments: List<Segment>,
    val stats: List<StatRow>
)

private val topicItems: List<TopicItem> = listOf(
    TopicItem(
        1,
        "NBA",
        "Player Performance",
        listOf(
            Segment.Link("Shai Gilgeous-Alexander"),
            Segment.Text(" dropped 52 points on 18-of-27 shooting as the "),
            Segment.Link("Thunder"),
            Segment.Text(" held off the "),
            Segment.Link("Nuggets"),
            Segment.Text(" in double overtime. It's his fourth 50-point game of the season, extending his lead in the MVP race over "),
            Segment.Link("Jokic"),
            Segment.Text("."),
        ),
        listOf(
            StatRow("SGA", "PTS", "52"),
            StatRow("SGA", "FG", "18-27"),
            StatRow("SGA", "PPG", "32.8"),
            StatRow("OKC", "REC", "58-18"),
        )
    ),
    TopicItem(
        2,
        "NBA",
        "Team Highlight",
        listOf(
            Segment.Text("The "),
            Segment.Link("Celtics"),
            Segment.Text(" clinched the top seed in the East with a 118-104 win over "),
            Segment.Link("Milwaukee"),
            Segment.Text(", their 14th straight home victory. "),
            Segment.Link("Jayson Tatum"),
            Segment.Text(" added 34 points and 11 rebounds, cementing Boston's case as title favorites."),
        ),
        listOf(
            StatRow("BOS", "REC", "62-18"),
            StatRow("Tatum", "PTS", "34"),
            StatRow("Tatum", "REB", "11"),
            StatRow("BOS", "HOME W", "14"),
        )
    ),
    TopicItem(
        3,
        "NBA",
        "Transaction",
        listOf(
            Segment.Text("The "),
            Segment.Link("Lakers"),
            Segment.Text(" acquired "),
            Segment.Link("Zach LaVine"),
            Segment.Text(" from the "),
            Segment.Link("Bulls"),
            Segment.Text(" in a three-team deal that sends "),
            Segment.Link("D'Angelo Russell"),
            Segment.Text(" and two first-round picks to Chicago. LaVine is expected to debut Friday against the "),
            Segment.Link("Warriors"),
            Segment.Text(" after passing his physical."),
        ),
        listOf(
            StatRow("LaVine", "PPG", "24.1"),
            StatRow("LaVine", "3P%", "38.2"),
            StatRow("LAL", "REC", "34-30"),
            StatRow("CHI", "PICKS", "2 1st"),
        )
    ),
    TopicItem(
        4,
        "NBA",
        "Milestone",
        listOf(
            Segment.Link("Nikola Jokic"),
            Segment.Text(" recorded his 30th triple-double of the season, the most ever by a center in a single year. "),
            Segment.Link("Denver"),
            Segment.Text(" has won nine straight with Jokic averaging 32-14-11 over that stretch."),
        ),
        listOf(
            StatRow("Jokic", "TD", "30"),
            StatRow("Jokic", "PPG", "32.0"),
            StatRow("Jokic", "RPG", "14.0"),
            StatRow("DEN", "STRK", "9"),
        )
    ),
    TopicItem(
        5,
        "NHL",
        "Streak",
        listOf(
            Segment.Link("Connor McDavid"),
            Segment.Text(" tallied four assists against the "),
            Segment.Link("Flames"),
            Segment.Text(", pushing his points streak to 18 games. He's within six points of "),
            Segment.Link("Wayne Gretzky"),
            Segment.Text("'s modern-era scoring pace from 1985-86."),
        ),
        listOf(
            StatRow("McDavid", "AST", "4"),
            StatRow("McDavid", "STRK", "18"),
            StatRow("McDavid", "PTS", "124"),
            StatRow("EDM", "REC", "44-18-4"),
        )
    ),
    TopicItem(
        6,
        "NHL",
        "Milestone",
        listOf(
            Segment.Link("Auston Matthews"),
            Segment.Text(" scored his 60th goal of the season in the Leafs' 5-2 win over "),
            Segment.Link("Tampa Bay"),
            Segment.Text(", becoming the first American-born player to hit 60 twice. Toronto clinched the Atlantic Division title with the victory."),
        ),
        listOf(
            StatRow("Matthews", "G", "60"),
            StatRow("Matthews", "GP", "68"),
            StatRow("TOR", "REC", "47-18-5"),
            StatRow("TOR", "FIN", "5-2"),
        )
    ),
    TopicItem(
        7,
        "NHL",
        "Transaction",
        listOf(
            Segment.Text("The "),
            Segment.Link("Rangers"),
            Segment.Text(" traded for defenseman "),
            Segment.Link("Jakob Chychrun"),
            Segment.Text(", sending a 2026 first-rounder and a conditional second to "),
            Segment.Link("Ottawa"),
            Segment.Text(". Chychrun gives New York a top-four left-shot blueliner for their Cup push."),
        ),
        listOf(
            StatRow("Chychrun", "G", "14"),
            StatRow("Chychrun", "PTS", "41"),
            StatRow("Chychrun", "TOI", "22:30"),
            StatRow("NYR", "REC", "48-17-5"),
        )
    ),
    TopicItem(
        8,
        "NBA",
        "Player Performance",
        listOf(
            Segment.Link("Victor Wembanyama"),
            Segment.Text(" posted the first 5x5 game by a rookie since "),
            Segment.Link("David Robinson"),
            Segment.Text(" in 1994, finishing with 27 points, 10 rebounds, 5 assists, 5 blocks, and 5 steals. The "),
            Segment.Link("Spurs"),
            Segment.Text(" still lost to "),
            Segment.Link("Phoenix"),
            Segment.Text(" in overtime, 132-128."),
        ),
        listOf(
            StatRow("Wemby", "PTS", "27"),
            StatRow("Wemby", "REB", "10"),
            StatRow("Wemby", "BLK", "5"),
            StatRow("Wemby", "STL", "5"),
        )
    ),
    TopicItem(
        9,
        "NHL",
        "Team Highlight",
        listOf(
            Segment.Text("The "),
            Segment.Link("Panthers"),
            Segment.Text(" extended their franchise-record winning streak to 13 games with a 4-1 victory over the "),
            Segment.Link("Bruins"),
            Segment.Text(". "),
            Segment.Link("Sergei Bobrovsky"),
            Segment.Text(" has been the difference, posting a .945 save percentage during the run."),
        ),
        listOf(
            StatRow("FLA", "STRK", "13"),
            StatRow("Bobrovsky", "SV%", ".945"),
            StatRow("Bobrovsky", "GAA", "1.88"),
            StatRow("FLA", "FIN", "4-1"),
        )
    ),
    TopicItem(
        10,
        "NBA",
        "Injury",
        listOf(
            Segment.Link("Luka Doncic"),
            Segment.Text(" was ruled out 4-6 weeks with a calf strain suffered late in Tuesday's loss to "),
            Segment.Link("Minnesota"),
            Segment.Text(". The injury threatens Dallas's playoff seeding as they sit one game up on the 7th-place "),
            Segment.Link("Kings"),
            Segment.Text("."),
        ),
        listOf(
            StatRow("Luka", "PPG", "33.5"),
            StatRow("Luka", "APG", "9.8"),
            StatRow("DAL", "REC", "40-26"),
            StatRow("DAL", "LEAD", "1.0 GB"),
        )
    ),
    TopicItem(
        11,
        "NHL",
        "Milestone",
        listOf(
            Segment.Link("Cale Makar"),
            Segment.Text(" recorded his 100th point of the season, becoming the fourth defenseman in NHL history to hit the mark. "),
            Segment.Link("Colorado"),
            Segment.Text("'s blueliner is the odds-on "),
            Segment.Link("Norris Trophy"),
            Segment.Text(" favorite for the third straight year."),
        ),
        listOf(
            StatRow("Makar", "PTS", "100"),
            StatRow("Makar", "G", "24"),
            StatRow("Makar", "AST", "76"),
            StatRow("COL", "REC", "46-20-6"),
        )
    ),
    TopicItem(
        12,
        "NBA",
        "Transaction",
        listOf(
            Segment.Text("The "),
            Segment.Link("Knicks"),
            Segment.Text(" acquired "),
            Segment.Link("OG Anunoby"),
            Segment.Text(" from Toronto ahead of the trade deadline, giving up "),
            Segment.Link("RJ Barrett"),
            Segment.Text(" and "),
            Segment.Link("Immanuel Quickley"),
            Segment.Text(". New York has gone 8-1 since the deal, surging into second place in the East."),
        ),
        listOf(
            StatRow("Anunoby", "PPG", "14.1"),
            StatRow("Anunoby", "3P%", "37.8"),
            StatRow("NYK", "REC (POST)", "8-1"),
            StatRow("NYK", "STANDING", "2nd"),
        )
    ),
    TopicItem(
        13,
        "NHL",
        "Playoffs",
        listOf(
            Segment.Text("The "),
            Segment.Link("Oilers"),
            Segment.Text(" opened their first-round series with a 5-2 win over the "),
            Segment.Link("Kings"),
            Segment.Text(" as "),
            Segment.Link("Connor McDavid"),
            Segment.Text(" notched a hat trick in Game 1. Edmonton has now won 12 straight playoff games dating back to last year's Western Conference Finals."),
        ),
        listOf(
            StatRow("McDavid", "G", "3"),
            StatRow("McDavid", "PTS", "4"),
            StatRow("EDM", "FIN", "5-2"),
            StatRow("EDM", "PO STRK", "12"),
        )
    ),
    TopicItem(
        14,
        "NBA",
        "Draft",
        listOf(
            Segment.Text("Duke freshman "),
            Segment.Link("Cooper Flagg"),
            Segment.Text(" cemented his place as the consensus #1 pick in the 2026 NBA Draft after a dominant Final Four performance. The "),
            Segment.Link("Wizards"),
            Segment.Text(" hold the best lottery odds at 14% following their 18-64 finish."),
        ),
        listOf(
            StatRow("Flagg", "PPG", "18.4"),
            StatRow("Flagg", "RPG", "8.2"),
            StatRow("Flagg", "BPG", "1.9"),
            StatRow("WAS", "LOT %", "14.0"),
        )
    ),
)

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
    modifier: Modifier = Modifier
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
            fontFamily = FontFamily.SansSerif,
            color = accentColor
        )
        Text(
            text = "  \u2022  ",
            fontSize = size,
            fontFamily = FontFamily.SansSerif,
            color = labelColor
        )
        Text(
            text = category.uppercase(),
            fontSize = size,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = labelColor
        )
    }
}

@Composable
private fun SegmentedTopicText(
    segments: List<Segment>,
    fontSize: Float,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground

    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        segments.forEachIndexed { idx, seg ->
            when (seg) {
                is Segment.Text -> append(seg.text)
                is Segment.Link -> {
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ) { append(seg.text) }
                    val id = "link_$idx"
                    appendInlineContent(id, "\u2197")
                    inlineContent[id] = InlineTextContent(
                        Placeholder(
                            width = 1.1.em,
                            height = 1.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = linkColor,
                            modifier = Modifier.fillMaxSize().padding(start = 2.dp)
                        )
                    }
                }
            }
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContent,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Light,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.4f).sp,
        color = textColor,
        modifier = modifier
    )
}

@Composable
private fun DataPointsSection(
    stats: List<StatRow>,
    fontSize: Float,
    modifier: Modifier = Modifier
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onBackground
    val headerSize = (fontSize * 0.6f).sp
    val rowSize = (fontSize * 0.72f).sp

    Column(modifier = modifier) {
        Text(
            text = "DATA POINTS",
            fontSize = headerSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = labelColor,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        stats.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.subject,
                    fontSize = rowSize,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    color = valueColor,
                    modifier = Modifier.weight(2f)
                )
                Text(
                    text = row.stat,
                    fontSize = rowSize,
                    fontFamily = FontFamily.SansSerif,
                    color = labelColor,
                    modifier = Modifier.weight(1.5f)
                )
                Text(
                    text = row.value,
                    fontSize = rowSize,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TopicsV2Screen(
    component: TopicsV2Component
) {
    val items = remember { mutableStateListOf(*topicItems.toTypedArray()) }
    var headerVisible by remember { mutableStateOf(true) }
    var showSizeSlider by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(18f) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            if (headerVisible) {
                Column {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = component.onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = { showSizeSlider = !showSizeSlider }) {
                                Text(
                                    text = "Aa",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 18.sp
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
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    // Size slider
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

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().then(
                    if (!headerVisible) Modifier.padding(top = 16.dp) else Modifier
                )
            ) {
                items(
                    items = items,
                    key = { it.id }
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
                            TopicLabel(
                                league = item.league,
                                category = item.category,
                                fontSize = fontSize,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            SegmentedTopicText(
                                segments = item.segments,
                                fontSize = fontSize,
                                modifier = Modifier.fillMaxWidth()
                            )
                            DataPointsSection(
                                stats = item.stats,
                                fontSize = fontSize,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }

            // Small FAB to reopen header
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

            // FAB to jump to next list item
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
}
