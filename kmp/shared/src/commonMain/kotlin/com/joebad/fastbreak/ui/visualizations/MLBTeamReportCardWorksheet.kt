package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.MLBTeamReportCardVisualization
import com.joebad.fastbreak.data.model.PinnedTeam
import com.joebad.fastbreak.data.model.ReportCardCategory
import com.joebad.fastbreak.data.model.ReportCardPlayer
import com.joebad.fastbreak.data.model.ReportCardStatValue
import com.joebad.fastbreak.data.model.ReportCardTeam
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.components.FabOption
import com.joebad.fastbreak.ui.components.InfoBottomSheet
import com.joebad.fastbreak.ui.components.MultiOptionFab

private fun filterReportCardTeams(
    teams: List<ReportCardTeam>,
    searchQuery: String
): List<ReportCardTeam> {
    if (searchQuery.isBlank()) return teams
    return teams.filter { team ->
        team.teamName.contains(searchQuery, ignoreCase = true) ||
            team.teamCode.contains(searchQuery, ignoreCase = true) ||
            team.division?.contains(searchQuery, ignoreCase = true) == true ||
            team.league?.contains(searchQuery, ignoreCase = true) == true
    }
}

private data class CategoryConfig(
    val statKeys: List<String>,
    val teamRankColorFn: (Int?) -> Color
)

private val CATEGORY_CONFIGS = mapOf(
    "hitters" to CategoryConfig(listOf("wRC_plus", "xwOBA"), ::getMLBTeamRankColor),
    "starters" to CategoryConfig(listOf("K-BB_pct", "xFIP"), ::getMLBTeamRankColor),
    "relievers" to CategoryConfig(listOf("K-BB_pct", "FIP"), ::getMLBTeamRankColor),
    "fielders" to CategoryConfig(listOf("OAA", "DRS"), ::getMLBTeamRankColor)
)

private enum class ReportCardShareTarget(val categoryKey: String?, val shareLabel: String) {
    HITTERS("hitters", "Hitters"),
    STARTERS("starters", "Starting Pitchers"),
    RELIEVERS("relievers", "Bullpen"),
    FIELDERS("fielders", "Fielders"),
    FULL(null, "Full Report Card")
}

private data class ReportCardCaptureRequest(
    val target: ReportCardShareTarget,
    val title: String
)

private fun teamCategoryEntries(team: ReportCardTeam): List<Pair<String, ReportCardCategory>> =
    listOf(
        "hitters" to team.categories.hitters,
        "starters" to team.categories.starters,
        "relievers" to team.categories.relievers,
        "fielders" to team.categories.fielders
    )

@Composable
fun MLBTeamReportCardWorksheet(
    visualization: MLBTeamReportCardVisualization,
    modifier: Modifier = Modifier,
    pinnedTeams: List<PinnedTeam> = emptyList(),
    highlightedTeamCodes: Set<String> = emptySet()
) {
    val mlbPinnedCodes = remember(pinnedTeams) {
        pinnedTeams.filter { it.sport == "MLB" }.map { it.teamCode }
    }

    val teams = remember(visualization.teams, mlbPinnedCodes) {
        val allTeams = visualization.teams.values
        val pinnedSet = mlbPinnedCodes.map { it.uppercase() }.toSet()
        val pinnedTeamsList = mlbPinnedCodes.mapNotNull { code ->
            allTeams.find { it.teamCode.equals(code, ignoreCase = true) }
        }
        val remainingTeams = allTeams
            .filter { it.teamCode.uppercase() !in pinnedSet }
            .sortedBy { it.teamName }
        pinnedTeamsList + remainingTeams
    }

    val defaultTeamCode = remember(teams, mlbPinnedCodes, highlightedTeamCodes) {
        mlbPinnedCodes.firstOrNull { code ->
            teams.any { it.teamCode.equals(code, ignoreCase = true) }
        }?.let { pinnedCode ->
            teams.first { it.teamCode.equals(pinnedCode, ignoreCase = true) }.teamCode
        }
            ?: highlightedTeamCodes.firstOrNull { code ->
                teams.any { it.teamCode.equals(code, ignoreCase = true) }
            }?.let { highlightCode ->
                teams.first { it.teamCode.equals(highlightCode, ignoreCase = true) }.teamCode
            }
            ?: teams.firstOrNull()?.teamCode
            ?: ""
    }

    var selectedTeamCode by remember(defaultTeamCode) { mutableStateOf(defaultTeamCode) }
    var teamPickerOpen by remember { mutableStateOf(false) }
    var teamSearchQuery by remember { mutableStateOf("") }
    var selectedRankingKey by remember { mutableStateOf<String?>(null) }
    var showPlayoffChances by remember { mutableStateOf(false) }

    val seasonLabel = remember(visualization.season) {
        val nextYear = (visualization.season + 1) % 100
        "${visualization.season}-$nextYear"
    }

    val filteredTeams = remember(teams, teamSearchQuery) {
        filterReportCardTeams(teams, teamSearchQuery)
    }

    val selectedTeam = teams.find { it.teamCode == selectedTeamCode }
    var captureRequest by remember { mutableStateOf<ReportCardCaptureRequest?>(null) }
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { teamPickerOpen = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedTeam?.teamName ?: "Select team",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select team",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        if (selectedTeam != null) {
            Text(
                text = formatReportCardTeamSubtitle(selectedTeam),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            ReportCardTeamSummaryRow(
                team = selectedTeam,
                hasRecordRankings = visualization.rankings.containsKey("record"),
                hasOverallRankings = visualization.rankings.containsKey("overallComposite"),
                hasPlayoffChances = visualization.playoffChances.isNotEmpty(),
                onRecordRankClick = {
                    if (visualization.rankings.containsKey("record")) {
                        selectedRankingKey = "record"
                    }
                },
                onOverallRankClick = {
                    if (visualization.rankings.containsKey("overallComposite")) {
                        selectedRankingKey = "overallComposite"
                    }
                },
                onPlayoffClick = {
                    if (visualization.playoffChances.isNotEmpty()) {
                        showPlayoffChances = true
                    }
                }
            )

            if (showPlayoffChances) {
                PlayoffChancesBottomSheet(
                    title = "$seasonLabel / Playoff Chances",
                    champLabel = "WS",
                    entries = visualization.playoffChances,
                    onDismiss = { showPlayoffChances = false },
                    probColorFn = ::getMLBPlayoffProbabilityColor,
                    highlightedTeams = setOf(selectedTeam.teamCode),
                    subtitle = "Season Projections",
                    source = "PlayoffStatus.com",
                    playoffCutoff = 6,
                    playInCutoff = 0,
                    showPlayoffColumn = true,
                    extraColumns = listOf(
                        PlayoffExtraColumn(
                            label = "W-L",
                            format = { entry ->
                                if (entry.wins != null && entry.losses != null) {
                                    "${entry.wins}-${entry.losses}"
                                } else {
                                    "-"
                                }
                            },
                            sortValue = { it.winPct ?: 0.0 }
                        ),
                        PlayoffExtraColumn(
                            label = "WIN%",
                            format = { entry ->
                                entry.winPct?.let { ".${(it * 1000).toInt()}" } ?: "-"
                            },
                            sortValue = { it.winPct ?: 0.0 }
                        )
                    )
                )
            }
        }

        selectedRankingKey?.let { key ->
            val entries = visualization.rankings[key] ?: emptyList()
            if (entries.isNotEmpty() && selectedTeam != null) {
                val statLabel = when (key) {
                    "record" -> "$seasonLabel / Record"
                    "overallComposite" -> "$seasonLabel / Overall Composite"
                    else -> "$seasonLabel / $key"
                }
                StatRankingsBottomSheet(
                    statLabel = statLabel,
                    entries = entries,
                    onDismiss = { selectedRankingKey = null },
                    rankColorFn = ::getMLBTeamRankColor,
                    highlightedTeams = setOf(selectedTeam.teamCode),
                    isPct = key == "record",
                    subtitle = "Season Rankings",
                    source = visualization.source ?: "FanGraphs"
                )
            }
        }

        if (teamPickerOpen) {
            InfoBottomSheet(
                onDismiss = {
                    teamPickerOpen = false
                    teamSearchQuery = ""
                },
                title = "Select team"
            ) {
                OutlinedTextField(
                    value = teamSearchQuery,
                    onValueChange = { teamSearchQuery = it },
                    placeholder = { Text("search teams...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search teams"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                if (filteredTeams.isEmpty()) {
                    Text(
                        text = "No teams found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    filteredTeams.forEach { team ->
                        val isSelected = team.teamCode == selectedTeamCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTeamCode = team.teamCode
                                    teamPickerOpen = false
                                    teamSearchQuery = ""
                                }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = team.teamName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = formatReportCardTeamSubtitle(team),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedTeam == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No team data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val categories = teamCategoryEntries(selectedTeam)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 80.dp)
            ) {
                categories.forEachIndexed { index, (key, category) ->
                    val config = CATEGORY_CONFIGS[key] ?: return@forEachIndexed
                    ReportCardCategorySection(
                        category = category,
                        statKeys = config.statKeys,
                        teamRankColorFn = config.teamRankColorFn
                    )
                    if (index < categories.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                buildReportCardSourceAttribution(visualization)?.let { attribution ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Source: $attribution",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }
        }

        if (selectedTeam != null) {
            val shareOptions = ReportCardShareTarget.entries.map { target ->
                FabOption(
                    icon = Icons.Filled.Share,
                    label = target.shareLabel,
                    onClick = {
                        captureRequest = ReportCardCaptureRequest(
                            target = target,
                            title = "${selectedTeam.teamCode} Report Card - ${target.shareLabel}"
                        )
                    }
                )
            }
            MultiOptionFab(
                options = shareOptions,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }

        captureRequest?.let { request ->
            val team = selectedTeam ?: return@let
            val categoriesToShare = teamCategoryEntries(team).filter { (key, _) ->
                request.target.categoryKey == null || key == request.target.categoryKey
            }

            LaunchedEffect(request) {
                kotlinx.coroutines.delay(50)
                try {
                    val bitmap = graphicsLayer.toImageBitmap()
                    imageExporter.shareImage(bitmap, request.title)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    captureRequest = null
                }
            }

            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(unbounded = true)
                        .offset { IntOffset(-10000, 0) }
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                ) {
                    MLBTeamReportCardShareImage(
                        team = team,
                        seasonLabel = seasonLabel,
                        source = visualization.source ?: "FanGraphs",
                        categories = categoriesToShare,
                        showSummary = request.target.categoryKey == null
                    )
                }
            }
        }
    }
}

private val PLAYER_NAME_COLUMN_WIDTH = 108.dp
private val PLAYER_ROW_MIN_HEIGHT = 24.dp
private val PLAYER_POS_WIDTH = 40.dp
private val PLAYER_WAR_WIDTH = 32.dp
private val PLAYER_STAT_WIDTH = 44.dp
private val PLAYER_RANK_WIDTH = 32.dp
private val PLAYER_RANK_LEFT_PADDING = 6.dp
private val PLAYER_COMP_WIDTH = 36.dp
private val PLAYER_STAT_GROUP_SPACING = 8.dp

@Composable
private fun ReportCardCategorySection(
    category: ReportCardCategory,
    statKeys: List<String>,
    teamRankColorFn: (Int?) -> Color,
    expandStatsForShare: Boolean = false
) {
    val teamStats = category.team?.stats.orEmpty()
    val primaryStats = statKeys.mapNotNull { key -> teamStats[key]?.let { key to it } }

    SectionHeader(category.label)

    primaryStats.forEach { (_, stat) ->
        FiveColumnRowWithRanks(
            leftValue = formatReportCardStat(stat),
            leftRank = stat.rank,
            leftRankDisplay = stat.rankDisplay,
            centerText = stat.label,
            rightValue = "",
            rightRank = null,
            rightRankDisplay = null,
            rankColorFn = teamRankColorFn,
            emptyRankPlaceholder = ""
        )
    }
    teamStats["aggregate"]?.let { composite ->
        FiveColumnRowWithRanks(
            leftValue = formatReportCardStat(composite),
            leftRank = composite.rank,
            leftRankDisplay = composite.rankDisplay,
            centerText = composite.label,
            rightValue = "",
            rightRank = null,
            rightRankDisplay = null,
            rankColorFn = teamRankColorFn,
            emptyRankPlaceholder = ""
        )
    }

    if (category.players.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        ReportCardPlayersSection(
            players = category.players,
            statKeys = statKeys,
            expandStatsForShare = expandStatsForShare
        )
    }

    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ReportCardPlayersSection(
    players: List<ReportCardPlayer>,
    statKeys: List<String>,
    expandStatsForShare: Boolean = false
) {
    val scrollState = rememberScrollState()
    val statLabels = statKeys.map { key ->
        players.firstNotNullOfOrNull { it.stats[key]?.label } ?: key
    }

    ReportCardPlayerLine(
        scrollState = scrollState,
        playerName = null,
        player = null,
        statLabels = statLabels,
        statKeys = statKeys,
        expandStatsForShare = expandStatsForShare
    )

    players.forEach { player ->
        ReportCardPlayerLine(
            scrollState = scrollState,
            playerName = player.name,
            player = player,
            statLabels = statLabels,
            statKeys = statKeys,
            expandStatsForShare = expandStatsForShare
        )
    }
}

@Composable
private fun ReportCardPlayerLine(
    scrollState: ScrollState,
    playerName: String?,
    player: ReportCardPlayer?,
    statLabels: List<String>,
    statKeys: List<String>,
    expandStatsForShare: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PLAYER_ROW_MIN_HEIGHT)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(PLAYER_NAME_COLUMN_WIDTH)
                .padding(start = 8.dp, end = 4.dp)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.CenterStart
        ) {
            if (playerName != null) {
                Text(
                    text = playerName,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (expandStatsForShare) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReportCardPlayerStatsColumns(
                    player = player,
                    statLabels = statLabels,
                    statKeys = statKeys
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState, overscrollEffect = null)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReportCardPlayerStatsColumns(
                    player = player,
                    statLabels = statLabels,
                    statKeys = statKeys
                )
            }
        }
    }
}

@Composable
private fun ReportCardPlayerStatsColumns(
    player: ReportCardPlayer?,
    statLabels: List<String>,
    statKeys: List<String>
) {
    val isHeader = player == null
    val headerStyle = MaterialTheme.typography.labelSmall
    val headerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val stat1 = statKeys.getOrNull(0)?.let { player?.stats?.get(it) }
    val stat2 = statKeys.getOrNull(1)?.let { player?.stats?.get(it) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ReportCardFixedColumn(PLAYER_POS_WIDTH, Alignment.Center) {
            if (isHeader) {
                ReportCardHeaderText("Pos", headerStyle, headerColor, TextAlign.Center)
            } else {
                ReportCardStatText(player?.position.orEmpty(), TextAlign.Center, muted = true)
            }
        }

        ReportCardFixedColumn(PLAYER_WAR_WIDTH, Alignment.CenterEnd) {
            if (isHeader) {
                ReportCardHeaderText("WAR", headerStyle, headerColor, TextAlign.End)
            } else {
                ReportCardStatText(
                    player?.war?.let { formatReportCardValue(it, decimals = 1) }.orEmpty(),
                    TextAlign.End
                )
            }
        }

        statLabels.forEachIndexed { index, label ->
            val stat = if (index == 0) stat1 else stat2

            Spacer(modifier = Modifier.width(PLAYER_STAT_GROUP_SPACING))

            ReportCardFixedColumn(PLAYER_STAT_WIDTH, Alignment.CenterEnd) {
                if (isHeader) {
                    ReportCardHeaderText(label, headerStyle, headerColor, TextAlign.End)
                } else {
                    ReportCardStatText(stat?.let { formatReportCardStat(it) }.orEmpty(), TextAlign.End)
                }
            }

            ReportCardFixedColumn(PLAYER_RANK_WIDTH, Alignment.Center, startPadding = PLAYER_RANK_LEFT_PADDING) {
                if (isHeader) {
                    ReportCardHeaderText("Rk", headerStyle, headerColor, TextAlign.Center)
                } else {
                    MatchupRankBadge(
                        rank = stat?.rank,
                        rankDisplay = stat?.rankDisplay,
                        rankColorFn = ::getMLBPlayerRankColor,
                        emptyPlaceholder = ""
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(PLAYER_STAT_GROUP_SPACING))

        ReportCardFixedColumn(PLAYER_COMP_WIDTH, Alignment.CenterEnd) {
            if (isHeader) {
                ReportCardHeaderText("Comp", headerStyle, headerColor, TextAlign.End)
            } else {
                ReportCardStatText(
                    player?.stats?.get("aggregate")?.let { formatReportCardStat(it) }.orEmpty(),
                    TextAlign.End,
                    muted = true
                )
            }
        }
    }
}

@Composable
private fun ReportCardFixedColumn(
    width: Dp,
    alignment: Alignment,
    startPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(start = startPadding),
        contentAlignment = alignment
    ) {
        content()
    }
}

@Composable
private fun ReportCardHeaderText(
    text: String,
    style: TextStyle,
    color: Color,
    textAlign: TextAlign
) {
    Text(
        text = text,
        style = style,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReportCardStatText(
    text: String,
    textAlign: TextAlign,
    muted: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontSize = if (muted) 10.sp else 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatTeamRecord(wins: Int?, losses: Int?): String? {
    if (wins == null || losses == null) return null
    return "$wins-$losses"
}

private fun formatReportCardTeamSubtitle(team: ReportCardTeam): String {
    val divisionPart = when {
        team.division != null && !team.divisionRankDisplay.isNullOrBlank() ->
            "${team.divisionRankDisplay} in ${team.division}"
        team.division != null -> team.division
        !team.divisionRankDisplay.isNullOrBlank() -> team.divisionRankDisplay
        else -> null
    }

    return listOfNotNull(
        team.teamCode,
        divisionPart,
        team.league,
        formatTeamRecord(team.wins, team.losses)
    ).joinToString(" • ")
}

private fun buildReportCardSourceAttribution(
    visualization: MLBTeamReportCardVisualization
): String? {
    val sources = buildList {
        visualization.source?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (visualization.playoffChances.isNotEmpty()) {
            add("PlayoffStatus.com")
        }
    }
    return sources.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

private fun formatMLBPlayoffProbability(prob: Double?): String {
    return if (prob != null) {
        if (prob >= 99.5) ">99%" else "${prob.toInt()}%"
    } else {
        "-"
    }
}

private fun getMLBPlayoffProbabilityColor(prob: Double?): Color {
    if (prob == null) return Color.Gray
    val p = prob.coerceIn(0.0, 100.0)
    return when {
        p <= 5.0 -> {
            val t = (p / 5.0).toFloat()
            Color(
                red = 0.7f + 0.2f * t,
                green = 0.1f + 0.4f * t,
                blue = 0f,
                alpha = 1f
            )
        }
        else -> Color(0xFF228B22)
    }
}

@Composable
private fun ReportCardPlayoffProbBadge(prob: Double?) {
    Box(
        modifier = Modifier
            .width(32.dp)
            .background(getMLBPlayoffProbabilityColor(prob), RoundedCornerShape(4.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatMLBPlayoffProbability(prob),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun ReportCardTeamSummaryRow(
    team: ReportCardTeam,
    hasRecordRankings: Boolean,
    hasOverallRankings: Boolean,
    hasPlayoffChances: Boolean,
    onRecordRankClick: () -> Unit,
    onOverallRankClick: () -> Unit,
    onPlayoffClick: () -> Unit,
    interactive: Boolean = true
) {
    val showRecord = team.recordRank != null || !team.recordRankDisplay.isNullOrBlank()
    val showOverall = team.overallComposite != null ||
        team.overallCompositeRank != null ||
        !team.overallCompositeRankDisplay.isNullOrBlank()
    val showPlayoff = team.playoffProb != null

    if (!showRecord && !showOverall && !showPlayoff) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (showRecord) {
            Row(
                modifier = Modifier.then(
                    if (interactive && hasRecordRankings) Modifier.clickable(onClick = onRecordRankClick) else Modifier
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Record",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                MatchupRankBadge(
                    rank = team.recordRank,
                    rankDisplay = team.recordRankDisplay,
                    rankColorFn = ::getMLBTeamRankColor,
                    emptyPlaceholder = ""
                )
            }
        }

        if (showOverall) {
            Row(
                modifier = Modifier.then(
                    if (interactive && hasOverallRankings) Modifier.clickable(onClick = onOverallRankClick) else Modifier
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Overall",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                team.overallComposite?.let { composite ->
                    Text(
                        text = formatReportCardValue(composite, decimals = 1),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                MatchupRankBadge(
                    rank = team.overallCompositeRank,
                    rankDisplay = team.overallCompositeRankDisplay,
                    rankColorFn = ::getMLBTeamRankColor,
                    emptyPlaceholder = ""
                )
            }
        }

        if (showPlayoff) {
            Row(
                modifier = Modifier.then(
                    if (interactive && hasPlayoffChances) Modifier.clickable(onClick = onPlayoffClick) else Modifier
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playoffs",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                ReportCardPlayoffProbBadge(prob = team.playoffProb)
            }
        }
    }
}

private val FULL_REPORT_CARD_SHARE_PLAYER_LIMIT = 3

@Composable
private fun MLBTeamReportCardShareImage(
    team: ReportCardTeam,
    seasonLabel: String,
    source: String,
    categories: List<Pair<String, ReportCardCategory>>,
    showSummary: Boolean
) {
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onSurface
    val dimColor = onBg.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .requiredWidth(420.dp)
            .background(bg)
            .padding(16.dp)
    ) {
        Text(
            text = team.teamName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onBg,
            maxLines = 1
        )
        Text(
            text = listOfNotNull(
                seasonLabel,
                formatReportCardTeamSubtitle(team)
            ).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = dimColor,
            maxLines = 2
        )

        if (showSummary) {
            Spacer(modifier = Modifier.height(8.dp))
            ReportCardTeamSummaryRow(
                team = team,
                hasRecordRankings = false,
                hasOverallRankings = false,
                hasPlayoffChances = false,
                onRecordRankClick = {},
                onOverallRankClick = {},
                onPlayoffClick = {},
                interactive = false
            )
        }

        categories.forEachIndexed { index, (key, category) ->
            val config = CATEGORY_CONFIGS[key] ?: return@forEachIndexed
            val shareCategory = if (showSummary) {
                category.copy(players = category.players.take(FULL_REPORT_CARD_SHARE_PLAYER_LIMIT))
            } else {
                category
            }
            if (index > 0) {
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
            ReportCardCategorySection(
                category = shareCategory,
                statKeys = config.statKeys,
                teamRankColorFn = config.teamRankColorFn,
                expandStatsForShare = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = dimColor,
                maxLines = 1
            )
            Text(
                text = "fbrk.app",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = dimColor
            )
        }
    }
}

private fun formatReportCardStat(stat: ReportCardStatValue): String {
    val value = stat.value ?: return ""
    return when {
        stat.label.contains("%", ignoreCase = true) -> formatReportCardValue(value, 1)
        stat.label.equals("wRC+", ignoreCase = true) -> formatReportCardValue(value, 0)
        stat.label.equals("Composite", ignoreCase = true) -> formatReportCardValue(value, 1)
        stat.label.equals("OAA", ignoreCase = true) || stat.label.equals("DRS", ignoreCase = true) ->
            formatReportCardValue(value, 1)
        stat.label.equals("xwOBA", ignoreCase = true) -> formatReportCardValue(value, 3)
        else -> formatReportCardValue(value, 2)
    }
}

private fun formatReportCardValue(value: Double, decimals: Int): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> 10.0
    }
    val rounded = kotlin.math.round(value * multiplier) / multiplier
    return when (decimals) {
        0 -> rounded.toInt().toString()
        else -> {
            val str = rounded.toString()
            if (str.contains('.')) {
                val parts = str.split('.')
                "${parts[0]}.${parts[1].padEnd(decimals, '0').take(decimals)}"
            } else {
                "$str.${"0".repeat(decimals)}"
            }
        }
    }
}
