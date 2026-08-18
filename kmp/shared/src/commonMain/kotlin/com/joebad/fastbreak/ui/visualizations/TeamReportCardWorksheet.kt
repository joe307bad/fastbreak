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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.TeamReportCardVisualization
import com.joebad.fastbreak.data.model.PinnedTeam
import com.joebad.fastbreak.data.model.PlayoffChanceEntry
import com.joebad.fastbreak.data.model.RankingEntry
import com.joebad.fastbreak.data.model.ReportCardCategory
import com.joebad.fastbreak.data.model.ReportCardGameLogGame
import com.joebad.fastbreak.data.model.ReportCardGameLog
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

// Layout for one category's table. NFL payloads (and every payload written
// after this file was generalized) carry their own keys and flags; the map
// below is the fallback for MLB cards cached before that.
private data class CategoryConfig(
    val statKeys: List<String>,
    val playerStatKeys: List<String> = statKeys,
    val positionColumnLabel: String = "Pos",
    val showPlayerRankAndComposite: Boolean = true,
    val showStatusColumn: Boolean = false,
    val showTeamComposite: Boolean = true
)

private val CATEGORY_CONFIGS = mapOf(
    "recentTrend" to CategoryConfig(
        statKeys = listOf("record", "runDiffPerGame", "runsPerGame", "runsAllowedPerGame", "hitsPerGame", "hrsPerGame"),
        showPlayerRankAndComposite = false
    ),
    "hitters" to CategoryConfig(
        statKeys = listOf("wRC_plus", "AVG", "OPS_plus", "Barrel_pct")
    ),
    "starters" to CategoryConfig(
        statKeys = listOf("K-BB_pct", "xFIP", "SIERA", "ERA")
    ),
    "relievers" to CategoryConfig(
        statKeys = listOf("K-BB_pct", "FIP", "SV", "SIERA", "ERA")
    ),
    "fielders" to CategoryConfig(
        statKeys = listOf("OAA", "DRS", "FRP")
    ),
    "injuries" to CategoryConfig(
        statKeys = listOf("impact"),
        showPlayerRankAndComposite = false,
        showStatusColumn = true
    ),
    "belowReplacement" to CategoryConfig(
        statKeys = listOf("below_replacement_pa_pct"),
        playerStatKeys = listOf("PA", "wRC_plus"),
        showPlayerRankAndComposite = false,
        showTeamComposite = false
    )
)

private val CATEGORY_COMPOSITE_RANKING_KEYS = mapOf(
    "hitters" to "hittersComposite",
    "starters" to "startersComposite",
    "relievers" to "relieversComposite",
    "fielders" to "fieldersComposite",
    "injuries" to "injuriesComposite"
)

private data class ResolvedCategoryConfig(
    val statKeys: List<String>,
    val playerStatKeys: List<String>,
    val positionColumnLabel: String,
    val showPlayerRankAndComposite: Boolean,
    val showStatusColumn: Boolean,
    val showWarColumn: Boolean,
    val showTeamComposite: Boolean,
    val compositeRankingKey: String?
)

// Payload first, then the MLB fallback table, then whatever keys the stat maps
// actually carry — so a sport that ships a new position group renders without
// any change here. "aggregate" is the composite and is rendered separately.
private fun resolveCategoryConfig(
    categoryKey: String,
    category: ReportCardCategory
): ResolvedCategoryConfig {
    val fallback = CATEGORY_CONFIGS[categoryKey]
    val statKeys = category.statKeys
        ?: fallback?.statKeys
        ?: category.team?.stats.orEmpty().keys.filter { it != "aggregate" }
    val playerStatKeys = category.playerStatKeys
        ?: fallback?.playerStatKeys
        ?: category.players.firstOrNull()?.stats.orEmpty().keys.filter { it != "aggregate" }
    return ResolvedCategoryConfig(
        statKeys = statKeys.toList(),
        playerStatKeys = playerStatKeys.toList(),
        positionColumnLabel = category.positionColumnLabel
            ?: fallback?.positionColumnLabel ?: "Pos",
        showPlayerRankAndComposite = category.showPlayerRankAndComposite
            ?: fallback?.showPlayerRankAndComposite ?: true,
        showStatusColumn = category.showStatusColumn
            ?: fallback?.showStatusColumn ?: false,
        // WAR is a baseball stat; the NFL card has no equivalent, so the column
        // is dropped rather than rendered empty for every player.
        showWarColumn = category.showWarColumn
            ?: category.players.any { it.war != null },
        showTeamComposite = category.showTeamComposite
            ?: fallback?.showTeamComposite ?: true,
        compositeRankingKey = category.compositeRankingKey
            ?: CATEGORY_COMPOSITE_RANKING_KEYS[categoryKey]
    )
}

private fun reportCardStatRankingKey(categoryKey: String, statKey: String): String =
    "$categoryKey.$statKey"

private fun reportCardPlayerStatRankingKey(categoryKey: String, statKey: String): String =
    "$categoryKey.player.$statKey"

private data class ParsedReportCardRankingKey(
    val categoryKey: String,
    val statKey: String,
    val isPlayer: Boolean
)

private fun parseReportCardRankingKey(key: String): ParsedReportCardRankingKey? {
    val playerMarker = ".player."
    val playerIndex = key.indexOf(playerMarker)
    if (playerIndex > 0) {
        return ParsedReportCardRankingKey(
            categoryKey = key.substring(0, playerIndex),
            statKey = key.substring(playerIndex + playerMarker.length),
            isPlayer = true
        )
    }
    val dotIndex = key.indexOf('.')
    if (dotIndex > 0) {
        return ParsedReportCardRankingKey(
            categoryKey = key.substring(0, dotIndex),
            statKey = key.substring(dotIndex + 1),
            isPlayer = false
        )
    }
    return null
}

private fun isReportCardPlayerRankingKey(key: String): Boolean =
    key.contains(".player.")

// Category and stat display names come from the payload — every category
// carries its own label and every stat entry carries its own — so ranking
// sheet titles work for any sport. The hardcoded tables below only cover MLB
// cards cached before the payload carried them.
private data class ReportCardLabelIndex(
    val categoryLabels: Map<String, String> = emptyMap(),
    val statLabels: Map<String, String> = emptyMap()
) {
    fun categoryLabel(categoryKey: String): String =
        categoryLabels[categoryKey] ?: formatReportCardCategoryLabel(categoryKey)

    fun statLabel(categoryKey: String, statKey: String): String =
        statLabels["$categoryKey.$statKey"] ?: reportCardStatLabel(categoryKey, statKey)
}

private fun buildReportCardLabelIndex(teams: Collection<ReportCardTeam>): ReportCardLabelIndex {
    val categoryLabels = mutableMapOf<String, String>()
    val statLabels = mutableMapOf<String, String>()
    teams.forEach { team ->
        team.categories.forEach { (categoryKey, category) ->
            if (categoryKey !in categoryLabels) categoryLabels[categoryKey] = category.label
            category.team?.stats?.forEach { (statKey, stat) ->
                val key = "$categoryKey.$statKey"
                if (key !in statLabels) statLabels[key] = stat.label
            }
            category.players.forEach { player ->
                player.stats.forEach { (statKey, stat) ->
                    val key = "$categoryKey.$statKey"
                    if (key !in statLabels) statLabels[key] = stat.label
                }
            }
        }
    }
    return ReportCardLabelIndex(categoryLabels, statLabels)
}

private fun reportCardStatLabel(categoryKey: String, statKey: String): String {
    return when (categoryKey) {
        "hitters" -> when (statKey) {
            "wRC_plus" -> "wRC+"
            "AVG" -> "AVG"
            "OPS_plus" -> "OPS+"
            "Barrel_pct" -> "Barrel%"
            else -> statKey
        }
        "starters" -> when (statKey) {
            "K-BB_pct" -> "K-BB%"
            "xFIP" -> "xFIP"
            "SIERA" -> "SIERA"
            "ERA" -> "ERA"
            else -> statKey
        }
        "relievers" -> when (statKey) {
            "K-BB_pct" -> "K-BB%"
            "FIP" -> "FIP"
            "SV" -> "SV"
            "SV_per_G" -> "SV/G"
            "SIERA" -> "SIERA"
            "ERA" -> "ERA"
            else -> statKey
        }
        "fielders" -> when (statKey) {
            "OAA" -> "OAA"
            "DRS" -> "DRS"
            "FRP" -> "FRP"
            else -> statKey
        }
        "injuries" -> when (statKey) {
            "injured_count" -> "Injured"
            "injury_war" -> "WAR Lost"
            else -> statKey
        }
        "belowReplacement" -> when (statKey) {
            "below_replacement_pa_pct" -> "BR PA%"
            "PA" -> "PA"
            "wRC_plus" -> "wRC+"
            else -> statKey
        }
        "recentTrend" -> when (statKey) {
            "record" -> "Record"
            "runDiffPerGame" -> "Run Diff/G"
            "runsPerGame" -> "Runs/G"
            "runsAllowedPerGame" -> "RA/G"
            "hitsPerGame" -> "Hits/G"
            "hrsPerGame" -> "HR/G"
            else -> statKey
        }
        else -> statKey
    }
}

private fun formatReportCardCategoryLabel(categoryKey: String): String = when (categoryKey) {
    "recentTrend" -> "4 Week Trend"
    "hitters" -> "Hitters"
    "starters" -> "Starting Pitchers"
    "relievers" -> "Bullpen"
    "fielders" -> "Fielders"
    "injuries" -> "Injury Report"
    "belowReplacement" -> "Below-replacement performers"
    else -> categoryKey
}

private fun isReportCardRankingPct(key: String): Boolean {
    return key == "record" ||
        key.endsWith(".K-BB_pct") ||
        key.endsWith(".Barrel_pct") ||
        key.endsWith(".below_replacement_pa_pct") ||
        key.endsWith(".below_replacement_play_pct")
}

private fun formatReportCardRankingLabel(
    seasonLabel: String,
    key: String,
    labels: ReportCardLabelIndex = ReportCardLabelIndex()
): String {
    parseReportCardRankingKey(key)?.let { parsed ->
        return "$seasonLabel / ${labels.categoryLabel(parsed.categoryKey)} / ${labels.statLabel(parsed.categoryKey, parsed.statKey)}"
    }
    if (key == "record") return "$seasonLabel / Record"
    if (key == "overallComposite") return "$seasonLabel / Overall Composite"
    if (key.endsWith("Composite")) {
        val categoryKey = key.removeSuffix("Composite")
        return "$seasonLabel / ${labels.categoryLabel(categoryKey)} Composite"
    }
    return "$seasonLabel / $key"
}

private fun buildCategoryCompositeRanking(
    teams: List<ReportCardTeam>,
    categoryKey: String
): List<RankingEntry> {
    return teams.mapNotNull { team ->
        val category = team.categories[categoryKey] ?: return@mapNotNull null
        val aggregate = category.team?.stats?.get("aggregate")
        val rank = aggregate?.rank
        val value = aggregate?.value
        if (rank == null || value == null) return@mapNotNull null
        RankingEntry(
            rank = rank,
            rankDisplay = aggregate.rankDisplay ?: rank.toString(),
            value = value,
            team = team.teamCode
        )
    }.sortedBy { it.rank }
}

private fun hasReportCardRankings(
    rankings: Map<String, List<RankingEntry>>,
    key: String
): Boolean = !rankings[key].isNullOrEmpty()

// Fills in any category composite ranking the payload did not ship, derived
// from the per-team composites it did. Covers every category the cards carry,
// not just MLB's, so a new position group still gets a tappable ranking sheet.
private fun mergeReportCardRankings(
    rankings: Map<String, List<RankingEntry>>,
    teams: List<ReportCardTeam>
): Map<String, List<RankingEntry>> {
    if (teams.isEmpty()) return rankings
    val merged = rankings.toMutableMap()
    val categoryKeys = teams.flatMap { it.categories.keys }.distinct()
    categoryKeys.forEach { categoryKey ->
        val category = teams.firstNotNullOfOrNull { it.categories[categoryKey] }
        val rankingKey = resolveCategoryConfig(categoryKey, category ?: return@forEach)
            .compositeRankingKey ?: "${categoryKey}Composite"
        if (merged[rankingKey].isNullOrEmpty()) {
            val built = buildCategoryCompositeRanking(teams, categoryKey)
            if (built.isNotEmpty()) merged[rankingKey] = built
        }
    }
    return merged
}

// One share target per rendered category, plus the game log and the full card.
// categoryKey == null with isGameLog == false means "everything".
private data class ReportCardShareTarget(
    val categoryKey: String?,
    val shareLabel: String,
    val isGameLog: Boolean = false
)

private data class ReportCardCaptureRequest(
    val target: ReportCardShareTarget,
    val title: String
)

// Category order comes from the payload; a card that omits it falls back to the
// map's own insertion order, which the pipeline writes in display order.
private fun teamDisplayCategoryEntries(team: ReportCardTeam): List<Pair<String, ReportCardCategory>> {
    val order = team.categoryOrder?.takeIf { it.isNotEmpty() } ?: team.categories.keys.toList()
    return order.mapNotNull { key ->
        val category = team.categories[key] ?: return@mapNotNull null
        val hasContent = category.players.isNotEmpty() || !category.team?.stats.isNullOrEmpty()
        if (hasContent) key to category else null
    }
}

private fun teamGameLog(team: ReportCardTeam): ReportCardGameLog? =
    (team.gameLog ?: team.lastTenGames)?.takeIf { it.games.isNotEmpty() }

private fun teamShareTargets(team: ReportCardTeam): List<ReportCardShareTarget> {
    val categoryTargets = teamDisplayCategoryEntries(team).map { (key, category) ->
        ReportCardShareTarget(categoryKey = key, shareLabel = category.label)
    }
    val gameLogTarget = teamGameLog(team)?.let {
        listOf(ReportCardShareTarget(categoryKey = null, shareLabel = it.label, isGameLog = true))
    } ?: emptyList()
    return categoryTargets + gameLogTarget +
        ReportCardShareTarget(categoryKey = null, shareLabel = "Full Report Card")
}

private fun teamShareCategoryEntries(
    team: ReportCardTeam,
    target: ReportCardShareTarget
): List<Pair<String, ReportCardCategory>> {
    val entries = teamDisplayCategoryEntries(team)
    return if (target.categoryKey != null) {
        entries.filter { it.first == target.categoryKey }
    } else {
        // The full-card share drops the below-replacement table: it is a
        // diagnostic, and it makes the image very tall.
        entries.filter { it.first != "belowReplacement" }
    }
}

@Composable
fun TeamReportCardWorksheet(
    visualization: TeamReportCardVisualization,
    modifier: Modifier = Modifier,
    pinnedTeams: List<PinnedTeam> = emptyList(),
    highlightedTeamCodes: Set<String> = emptySet()
) {
    val sportPinnedCodes = remember(pinnedTeams, visualization.sport) {
        pinnedTeams.filter { it.sport.equals(visualization.sport, ignoreCase = true) }
            .map { it.teamCode }
    }

    val teams = remember(visualization.teams, sportPinnedCodes) {
        val allTeams = visualization.teams.values
        val pinnedSet = sportPinnedCodes.map { it.uppercase() }.toSet()
        val pinnedTeamsList = sportPinnedCodes.mapNotNull { code ->
            allTeams.find { it.teamCode.equals(code, ignoreCase = true) }
        }
        val remainingTeams = allTeams
            .filter { it.teamCode.uppercase() !in pinnedSet }
            .sortedBy { it.teamName }
        pinnedTeamsList + remainingTeams
    }

    val defaultTeamCode = remember(teams, sportPinnedCodes, highlightedTeamCodes) {
        sportPinnedCodes.firstOrNull { code ->
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
    val displayPlayoffChances = remember(visualization.playoffChances, visualization.teams) {
        organizeMlbPlayoffChancesForDisplay(visualization.playoffChances, visualization.teams)
    }

    var showPlayoffChances by remember { mutableStateOf(false) }

    // The pipeline ships a formatted season label; older payloads did not, so
    // fall back to the MLB-style "2025-26" the card has always shown.
    val seasonLabel = remember(visualization.seasonLabel, visualization.season) {
        visualization.seasonLabel?.takeIf { it.isNotBlank() }
            ?: "${visualization.season}-${((visualization.season + 1) % 100).toString().padStart(2, '0')}"
    }

    val labelIndex = remember(visualization.teams) {
        buildReportCardLabelIndex(visualization.teams.values)
    }

    val filteredTeams = remember(teams, teamSearchQuery) {
        filterReportCardTeams(teams, teamSearchQuery)
    }

    val reportCardRankings = remember(visualization.rankings, teams) {
        mergeReportCardRankings(visualization.rankings, teams)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { teamPickerOpen = true }
                .padding(bottom = 4.dp),
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
                hasRecordRankings = hasReportCardRankings(reportCardRankings, "record"),
                hasOverallRankings = hasReportCardRankings(reportCardRankings, "overallComposite"),
                hasPlayoffChances = visualization.playoffChances.isNotEmpty(),
                onRecordRankClick = {
                    if (hasReportCardRankings(reportCardRankings, "record")) {
                        selectedRankingKey = "record"
                    }
                },
                onOverallRankClick = {
                    if (hasReportCardRankings(reportCardRankings, "overallComposite")) {
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
                    entries = displayPlayoffChances,
                    onDismiss = { showPlayoffChances = false },
                    probColorFn = ::getMLBPlayoffProbabilityColor,
                    highlightedTeams = setOf(selectedTeam.teamCode),
                    subtitle = "Season Projections",
                    source = "PlayoffStatus.com",
                    playoffCutoff = 6,
                    playInCutoff = 0,
                    showPlayoffColumn = true,
                    useStandingsLayout = true,
                    wildCardPlayoffCutoff = 3,
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

        if (teamPickerOpen) {
            InfoBottomSheet(
                onDismiss = {
                    teamPickerOpen = false
                    teamSearchQuery = ""
                },
                title = "Select team",
                fullScreen = true
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
            val categories = teamDisplayCategoryEntries(selectedTeam)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 80.dp)
            ) {
                categories.forEachIndexed { index, (key, category) ->
                    val config = resolveCategoryConfig(key, category)
                    ReportCardCategorySection(
                        categoryKey = key,
                        category = category,
                        statKeys = config.statKeys,
                        playerStatKeys = config.playerStatKeys,
                        positionColumnLabel = config.positionColumnLabel,
                        showPlayerRankAndComposite = config.showPlayerRankAndComposite,
                        showStatusColumn = config.showStatusColumn,
                        showWarColumn = config.showWarColumn,
                        showTeamComposite = config.showTeamComposite,
                        compositeRankingKey = config.compositeRankingKey,
                        rankings = reportCardRankings,
                        onRankingClick = { selectedRankingKey = it }
                    )
                    if (index < categories.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                teamGameLog(selectedTeam)?.let { gameLog ->
                    Spacer(modifier = Modifier.height(12.dp))
                    ReportCardGameLogSection(gameLog)
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
            val shareOptions = teamShareTargets(selectedTeam)
                .map { target ->
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

        selectedRankingKey?.let { key ->
            val entries = reportCardRankings[key] ?: emptyList()
            val team = selectedTeam
            if (entries.isNotEmpty() && team != null) {
                StatRankingsBottomSheet(
                    statLabel = formatReportCardRankingLabel(seasonLabel, key, labelIndex),
                    entries = entries,
                    onDismiss = { selectedRankingKey = null },
                    rankColorFn = if (isReportCardPlayerRankingKey(key)) {
                        ::getMLBPlayerRankColor
                    } else {
                        ::getMLBTeamRankColor
                    },
                    highlightedTeams = setOf(team.teamCode),
                    isPct = isReportCardRankingPct(key),
                    subtitle = if (isReportCardPlayerRankingKey(key)) "Player Rankings" else "Season Rankings",
                    source = reportCardStatsSource(visualization)
                )
            }
        }

        captureRequest?.let { request ->
            val team = selectedTeam ?: return@let

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
                    if (request.target.isGameLog) {
                        TeamReportCardGameLogShareImage(
                            team = team,
                            seasonLabel = seasonLabel,
                            source = buildReportCardGameLogShareSource(visualization)
                        )
                    } else {
                        TeamReportCardShareImage(
                            team = team,
                            seasonLabel = seasonLabel,
                            source = buildReportCardShareSourceAttribution(
                                visualization,
                                request.target.categoryKey
                            ),
                            categories = teamShareCategoryEntries(team, request.target),
                            showSummary = request.target.categoryKey == null
                        )
                    }
                }
            }
        }
    }
}

private val PLAYER_NAME_COLUMN_WIDTH = 108.dp
private val PLAYER_ROW_MIN_HEIGHT = 24.dp
private val PLAYER_POS_WIDTH = 40.dp
private val PLAYER_STATUS_WIDTH = 52.dp
private val PLAYER_WAR_WIDTH = 32.dp
private val PLAYER_STAT_WIDTH = 44.dp
private val PLAYER_RANK_WIDTH = 40.dp
private val PLAYER_RANK_LEFT_PADDING = 6.dp
private val PLAYER_COMP_WIDTH = 36.dp
private val PLAYER_STAT_GROUP_SPACING = 8.dp

private val SHARE_REPORT_CARD_MIN_WIDTH = 240.dp
private val SHARE_REPORT_CARD_HORIZONTAL_PADDING = 32.dp

private fun reportCardSharePlayerStatsWidth(
    teamStatCount: Int,
    playerStatCount: Int,
    showPlayerRankAndComposite: Boolean,
    showStatusColumn: Boolean = false,
    showWarColumn: Boolean = true
): Dp {
    var width = PLAYER_POS_WIDTH
    if (showWarColumn) width += PLAYER_WAR_WIDTH
    if (showStatusColumn) width += PLAYER_STATUS_WIDTH
    val statCount = maxOf(teamStatCount, playerStatCount)
    repeat(statCount) {
        width += PLAYER_STAT_GROUP_SPACING + PLAYER_STAT_WIDTH
        if (showPlayerRankAndComposite) {
            width += PLAYER_RANK_LEFT_PADDING + PLAYER_RANK_WIDTH
        }
    }
    if (showPlayerRankAndComposite) {
        width += PLAYER_STAT_GROUP_SPACING + PLAYER_COMP_WIDTH
    }
    return width
}

private fun reportCardSharePlayerContentWidth(
    categories: List<Pair<String, ReportCardCategory>>
): Dp {
    val nameAndStatsPadding = 20.dp
    // Only categories that actually render player rows contribute to width.
    // Team-only sections (e.g. 4 Week Trend) have empty players and must not
    // inflate width from phantom stat columns.
    return categories.mapNotNull { (key, category) ->
        if (category.players.isEmpty()) return@mapNotNull null
        val config = resolveCategoryConfig(key, category)
        PLAYER_NAME_COLUMN_WIDTH + nameAndStatsPadding +
            reportCardSharePlayerStatsWidth(
                config.statKeys.size,
                config.playerStatKeys.size,
                config.showPlayerRankAndComposite,
                config.showStatusColumn,
                config.showWarColumn
            )
    }.maxOrNull() ?: 0.dp
}

@Composable
private fun rememberReportCardShareImageWidth(
    team: ReportCardTeam,
    seasonLabel: String,
    source: String,
    categories: List<Pair<String, ReportCardCategory>>,
    showSummary: Boolean
): Dp {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val subtitleLine = formatReportCardShareSubtitleLine(seasonLabel, team)
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val sourceStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)

    // Year • place in division • record is typically the widest text on
    // team-only shares (4 Week Trend). Measure as a single unwrapped line.
    val subtitleWidth = with(density) {
        textMeasurer.measure(
            text = subtitleLine,
            style = titleStyle,
            softWrap = false,
            maxLines = 1
        ).size.width.toDp()
    }
    val teamNameWidth = with(density) {
        textMeasurer.measure(
            text = team.teamName,
            style = titleStyle,
            softWrap = false,
            maxLines = 1
        ).size.width.toDp()
    }
    val sourceWidth = with(density) {
        textMeasurer.measure(
            text = source,
            style = sourceStyle,
            softWrap = false,
            maxLines = 1
        ).size.width.toDp()
    }
    val playerRowWidth = reportCardSharePlayerContentWidth(categories)

    return remember(
        team,
        seasonLabel,
        source,
        categories,
        showSummary,
        subtitleWidth,
        teamNameWidth,
        sourceWidth,
        playerRowWidth
    ) {
        listOf(
            playerRowWidth + SHARE_REPORT_CARD_HORIZONTAL_PADDING,
            subtitleWidth + SHARE_REPORT_CARD_HORIZONTAL_PADDING,
            teamNameWidth + SHARE_REPORT_CARD_HORIZONTAL_PADDING,
            sourceWidth + 72.dp + SHARE_REPORT_CARD_HORIZONTAL_PADDING,
            if (showSummary) 260.dp else 0.dp
        ).max().coerceAtLeast(SHARE_REPORT_CARD_MIN_WIDTH)
    }
}

private fun formatReportCardShareSubtitleLine(seasonLabel: String, team: ReportCardTeam): String {
    return listOfNotNull(seasonLabel, formatReportCardTeamSubtitle(team)).joinToString(" • ")
}

@Composable
private fun ReportCardCategorySection(
    categoryKey: String,
    category: ReportCardCategory,
    statKeys: List<String>,
    playerStatKeys: List<String> = statKeys,
    positionColumnLabel: String = "Pos",
    showPlayerRankAndComposite: Boolean = true,
    showStatusColumn: Boolean = false,
    showWarColumn: Boolean = true,
    showTeamComposite: Boolean = true,
    compositeRankingKey: String? = null,
    rankings: Map<String, List<RankingEntry>> = emptyMap(),
    onRankingClick: ((String) -> Unit)? = null,
    expandStatsForShare: Boolean = false
) {
    val teamStats = category.team?.stats.orEmpty()
    val primaryStats = statKeys.mapNotNull { key -> teamStats[key]?.let { key to it } }

    SectionHeader(category.label)

    primaryStats.forEach { (statKey, stat) ->
        val rankingKey = reportCardStatRankingKey(categoryKey, statKey)
        val rankingAvailable = hasReportCardRankings(rankings, rankingKey)
        FiveColumnRowWithRanks(
            leftValue = formatReportCardStat(stat),
            leftRank = stat.rank,
            leftRankDisplay = stat.rankDisplay,
            centerText = stat.label,
            rightValue = "",
            rightRank = null,
            rightRankDisplay = null,
            rankColorFn = ::getMLBTeamRankColor,
            useNBARanks = false,
            onClick = if (rankingAvailable) {
                { onRankingClick?.invoke(rankingKey) }
            } else {
                null
            },
            emptyRankPlaceholder = ""
        )
    }
    if (showTeamComposite) {
        teamStats["aggregate"]?.let { composite ->
            val compositeRankingAvailable =
                compositeRankingKey != null && hasReportCardRankings(rankings, compositeRankingKey)
            FiveColumnRowWithRanks(
                leftValue = formatReportCardStat(composite),
                leftRank = composite.rank,
                leftRankDisplay = composite.rankDisplay,
                centerText = composite.label,
                rightValue = "",
                rightRank = null,
                rightRankDisplay = null,
                rankColorFn = ::getMLBTeamRankColor,
                useNBARanks = false,
                onClick = if (compositeRankingAvailable) {
                    { onRankingClick?.invoke(compositeRankingKey!!) }
                } else {
                    null
                },
                emptyRankPlaceholder = ""
            )
        }
    }

    if (category.players.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        ReportCardPlayersSection(
            categoryKey = categoryKey,
            players = category.players,
            statKeys = playerStatKeys,
            positionColumnLabel = positionColumnLabel,
            showPlayerRankAndComposite = showPlayerRankAndComposite,
            showStatusColumn = showStatusColumn,
            showWarColumn = showWarColumn,
            rankings = rankings,
            onRankingClick = onRankingClick,
            expandStatsForShare = expandStatsForShare
        )
    }

    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ReportCardPlayersSection(
    categoryKey: String,
    players: List<ReportCardPlayer>,
    statKeys: List<String>,
    positionColumnLabel: String = "Pos",
    showPlayerRankAndComposite: Boolean = true,
    showStatusColumn: Boolean = false,
    showWarColumn: Boolean = true,
    rankings: Map<String, List<RankingEntry>> = emptyMap(),
    onRankingClick: ((String) -> Unit)? = null,
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
        categoryKey = categoryKey,
        statLabels = statLabels,
        statKeys = statKeys,
        positionColumnLabel = positionColumnLabel,
        showPlayerRankAndComposite = showPlayerRankAndComposite,
        showStatusColumn = showStatusColumn,
        showWarColumn = showWarColumn,
        rankings = rankings,
        onRankingClick = onRankingClick,
        expandStatsForShare = expandStatsForShare
    )

    players.forEach { player ->
        ReportCardPlayerLine(
            scrollState = scrollState,
            playerName = player.name,
            player = player,
            categoryKey = categoryKey,
            statLabels = statLabels,
            statKeys = statKeys,
            positionColumnLabel = positionColumnLabel,
            showPlayerRankAndComposite = showPlayerRankAndComposite,
            showStatusColumn = showStatusColumn,
            showWarColumn = showWarColumn,
            rankings = rankings,
            onRankingClick = onRankingClick,
            expandStatsForShare = expandStatsForShare
        )
    }
}

@Composable
private fun ReportCardPlayerLine(
    scrollState: ScrollState,
    playerName: String?,
    player: ReportCardPlayer?,
    categoryKey: String,
    statLabels: List<String>,
    statKeys: List<String>,
    positionColumnLabel: String = "Pos",
    showPlayerRankAndComposite: Boolean = true,
    showStatusColumn: Boolean = false,
    showWarColumn: Boolean = true,
    rankings: Map<String, List<RankingEntry>> = emptyMap(),
    onRankingClick: ((String) -> Unit)? = null,
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
                    .wrapContentWidth(unbounded = true)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReportCardPlayerStatsColumns(
                    categoryKey = categoryKey,
                    player = player,
                    statLabels = statLabels,
                    statKeys = statKeys,
                    positionColumnLabel = positionColumnLabel,
                    showPlayerRankAndComposite = showPlayerRankAndComposite,
                    showStatusColumn = showStatusColumn,
                    showWarColumn = showWarColumn,
                    rankings = rankings,
                    onRankingClick = onRankingClick
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
                    categoryKey = categoryKey,
                    player = player,
                    statLabels = statLabels,
                    statKeys = statKeys,
                    positionColumnLabel = positionColumnLabel,
                    showPlayerRankAndComposite = showPlayerRankAndComposite,
                    showStatusColumn = showStatusColumn,
                    showWarColumn = showWarColumn,
                    rankings = rankings,
                    onRankingClick = onRankingClick
                )
            }
        }
    }
}

@Composable
private fun ReportCardPlayerStatsColumns(
    categoryKey: String,
    player: ReportCardPlayer?,
    statLabels: List<String>,
    statKeys: List<String>,
    positionColumnLabel: String = "Pos",
    showPlayerRankAndComposite: Boolean = true,
    showStatusColumn: Boolean = false,
    showWarColumn: Boolean = true,
    rankings: Map<String, List<RankingEntry>> = emptyMap(),
    onRankingClick: ((String) -> Unit)? = null
) {
    val isHeader = player == null
    val headerStyle = MaterialTheme.typography.labelSmall
    val headerColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(verticalAlignment = Alignment.CenterVertically) {
        ReportCardFixedColumn(PLAYER_POS_WIDTH, Alignment.Center) {
            if (isHeader) {
                ReportCardHeaderText(positionColumnLabel, headerStyle, headerColor, TextAlign.Center)
            } else {
                ReportCardStatText(player?.position.orEmpty(), TextAlign.Center, muted = true)
            }
        }

        if (showStatusColumn) {
            ReportCardFixedColumn(PLAYER_STATUS_WIDTH, Alignment.CenterStart) {
                if (isHeader) {
                    ReportCardHeaderText("Status", headerStyle, headerColor, TextAlign.Start)
                } else {
                    ReportCardStatText(player?.status.orEmpty(), TextAlign.Start, muted = true)
                }
            }
        }

        if (showWarColumn) {
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
        }

        statLabels.forEachIndexed { index, label ->
            val statKey = statKeys.getOrNull(index)
            val stat = statKey?.let { player?.stats?.get(it) }
            val rankingKey = statKey?.let { reportCardPlayerStatRankingKey(categoryKey, it) }
            val rankingAvailable = statKey != null &&
                hasReportCardRankings(rankings, reportCardPlayerStatRankingKey(categoryKey, statKey))
            val statClickModifier = if (!isHeader && rankingAvailable) {
                Modifier.clickable { onRankingClick?.invoke(rankingKey!!) }
            } else {
                Modifier
            }

            Spacer(modifier = Modifier.width(PLAYER_STAT_GROUP_SPACING))

            Row(
                modifier = statClickModifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReportCardFixedColumn(PLAYER_STAT_WIDTH, Alignment.CenterEnd) {
                    if (isHeader) {
                        ReportCardHeaderText(label, headerStyle, headerColor, TextAlign.End)
                    } else {
                        ReportCardStatText(stat?.let { formatReportCardStat(it) }.orEmpty(), TextAlign.End)
                    }
                }

                if (showPlayerRankAndComposite) {
                    ReportCardFixedColumn(PLAYER_RANK_WIDTH, Alignment.Center, startPadding = PLAYER_RANK_LEFT_PADDING) {
                        if (isHeader) {
                            ReportCardHeaderText("Rk", headerStyle, headerColor, TextAlign.Center)
                        } else {
                            MatchupRankBadge(
                                rank = stat?.rank,
                                rankDisplay = stat?.rankDisplay,
                                rankColorFn = ::getMLBPlayerRankColor,
                                usePlayerRanks = true,
                                useNBARanks = false,
                                emptyPlaceholder = ""
                            )
                        }
                    }
                }
            }
        }

        if (showPlayerRankAndComposite) {
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

private fun formatTeamRecord(wins: Int?, losses: Int?, ties: Int? = null): String? {
    if (wins == null || losses == null) return null
    // The NFL is the only sport here that ties; MLB payloads carry no ties field.
    return if (ties != null && ties > 0) "$wins-$losses-$ties" else "$wins-$losses"
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
        divisionPart,
        formatTeamRecord(team.wins, team.losses, team.ties)
    ).joinToString(" • ")
}

private val MLB_STANDINGS_SECTION_ORDER = mapOf(
    "National" to listOf("NL East", "NL Central", "NL West", "Wild Card"),
    "American" to listOf("AL East", "AL Central", "AL West", "Wild Card")
)

private fun mlbGamesBack(w: Int, l: Int, wRef: Int, lRef: Int): Double =
    ((wRef - w) + (l - lRef)) / 2.0

private fun organizeMlbPlayoffChancesForDisplay(
    entries: List<PlayoffChanceEntry>,
    teams: Map<String, ReportCardTeam>
): List<PlayoffChanceEntry> {
    val uniqueEntries = entries
        .groupBy { it.team.uppercase() }
        .map { (_, group) ->
            group.firstOrNull { it.standingsSection != "Wild Card" } ?: group.first()
        }

    if (uniqueEntries.any { !it.standingsSection.isNullOrBlank() && it.standingsSection != "Wild Card" }) {
        return uniqueEntries
    }

    data class Enriched(
        val entry: PlayoffChanceEntry,
        val division: String?,
        val league: String?,
        val divisionRank: Int?,
        val wins: Int?,
        val losses: Int?,
        val winPct: Double?
    )

    fun enrich(entry: PlayoffChanceEntry): Enriched {
        val team = teams[entry.team]
            ?: teams.entries.find { it.key.equals(entry.team, ignoreCase = true) }?.value
        val wins = entry.wins ?: team?.wins
        val losses = entry.losses ?: team?.losses
        val winPct = entry.winPct ?: if (wins != null && losses != null && wins + losses > 0) {
            wins.toDouble() / (wins + losses)
        } else {
            null
        }
        val division = entry.division ?: team?.division
        val league = when {
            division?.startsWith("AL") == true -> "American"
            division?.startsWith("NL") == true -> "National"
            else -> entry.conference
        }
        return Enriched(
            entry = entry,
            division = division,
            league = league,
            divisionRank = entry.divisionRank ?: team?.divisionRank,
            wins = wins,
            losses = losses,
            winPct = winPct
        )
    }

    fun organizeLeague(
        leagueEntries: List<Enriched>,
        divisions: List<String>
    ): List<PlayoffChanceEntry> {
        if (leagueEntries.isEmpty()) return emptyList()

        val divLeaders = leagueEntries.filter { it.divisionRank == 1 }
        val nonLeaders = leagueEntries
            .filter { (it.divisionRank ?: Int.MAX_VALUE) > 1 }
            .sortedWith(compareByDescending<Enriched> { it.winPct ?: 0.0 }.thenByDescending { it.wins ?: 0 }.thenBy { it.losses ?: Int.MAX_VALUE })
        val wcWinners = nonLeaders.take(3)
        val playoffTeams = (divLeaders + wcWinners).sortedWith(
            compareBy<Enriched> { it.winPct ?: 1.0 }.thenBy { it.wins ?: Int.MAX_VALUE }.thenByDescending { it.losses ?: 0 }
        )
        val cutoff = playoffTeams.firstOrNull()
        val cutoffW = cutoff?.wins
        val cutoffL = cutoff?.losses

        fun gamesBackFor(item: Enriched): Double? {
            if (cutoffW == null || cutoffL == null || item.wins == null || item.losses == null) return null
            val gb = mlbGamesBack(item.wins, item.losses, cutoffW, cutoffL)
            return if (gb <= 0.0) 0.0 else kotlin.math.round(gb * 10) / 10
        }

        fun toEntry(item: Enriched, section: String): PlayoffChanceEntry {
            return item.entry.copy(
                conference = item.league ?: item.entry.conference,
                division = item.division,
                divisionRank = item.divisionRank,
                wins = item.wins,
                losses = item.losses,
                winPct = item.winPct,
                gamesBackFromPlayoff = gamesBackFor(item),
                standingsSection = section
            )
        }

        val organized = mutableListOf<PlayoffChanceEntry>()
        divisions.forEach { division ->
            leagueEntries
                .filter { it.division == division }
                .sortedWith(compareBy<Enriched> { it.divisionRank ?: Int.MAX_VALUE }.thenByDescending { it.winPct ?: 0.0 }.thenByDescending { it.wins ?: 0 })
                .forEach { organized += toEntry(it, division) }
        }
        return organized
    }

    val enriched = uniqueEntries.map(::enrich)
    return organizeLeague(enriched.filter { it.league == "National" }, listOf("NL East", "NL Central", "NL West")) +
        organizeLeague(enriched.filter { it.league == "American" }, listOf("AL East", "AL Central", "AL West"))
}

private fun parseReportCardSources(source: String): List<String> {
    return source
        .split(",", "•", "/", "|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun buildReportCardSourceAttribution(
    visualization: TeamReportCardVisualization
): String? {
    val sources = buildList {
        visualization.source?.takeIf { it.isNotBlank() }?.let { raw ->
            addAll(parseReportCardSources(raw))
        }
        if (visualization.playoffChances.isNotEmpty()) {
            add("PlayoffStatus.com")
        }
    }.distinct()
    return sources.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

// Ranking sheets show only the stat providers, never the injury feed, since no
// ranking on them comes from ESPN.
private fun reportCardStatsSource(visualization: TeamReportCardVisualization): String {
    val fallback = visualization.source ?: "FanGraphs"
    return parseReportCardSources(fallback)
        .filterNot { it.equals("ESPN", ignoreCase = true) }
        .distinct()
        .joinToString(" • ")
        .ifBlank { fallback }
}

private fun buildReportCardShareSourceAttribution(
    visualization: TeamReportCardVisualization,
    categoryKey: String?
): String {
    return when (categoryKey) {
        "recentTrend" -> "ESPN"
        "injuries" -> {
            val sources = parseReportCardSources(visualization.source ?: "FanGraphs • ESPN")
                .filter { source ->
                    source.equals("FanGraphs", ignoreCase = true) ||
                        source.equals("ESPN", ignoreCase = true)
                }
                .distinct()
            if (sources.isEmpty()) "FanGraphs • ESPN" else sources.joinToString(" • ")
        }
        null -> {
            parseReportCardSources(visualization.source ?: "FanGraphs • ESPN")
                .distinct()
                .joinToString(" • ")
                .ifBlank { "FanGraphs • ESPN" }
        }
        else -> "FanGraphs"
    }
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
private fun TeamReportCardShareImage(
    team: ReportCardTeam,
    seasonLabel: String,
    source: String,
    categories: List<Pair<String, ReportCardCategory>>,
    showSummary: Boolean
) {
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onSurface
    val shareWidth = rememberReportCardShareImageWidth(team, seasonLabel, source, categories, showSummary)

    Column(
        modifier = Modifier
            .width(shareWidth)
            .wrapContentHeight()
            .background(bg)
            .padding(16.dp)
    ) {
        Text(
            text = team.teamName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onBg,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatReportCardShareSubtitleLine(seasonLabel, team),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onBg,
            maxLines = 1,
            softWrap = false
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
            val config = resolveCategoryConfig(key, category)
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
                categoryKey = key,
                category = shareCategory,
                statKeys = config.statKeys,
                playerStatKeys = config.playerStatKeys,
                positionColumnLabel = config.positionColumnLabel,
                showPlayerRankAndComposite = config.showPlayerRankAndComposite,
                showStatusColumn = config.showStatusColumn,
                showWarColumn = config.showWarColumn,
                showTeamComposite = config.showTeamComposite,
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
                color = onBg,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = "fbrk.app",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = onBg
            )
        }
    }
}

// ============================================================================
// Last 10 games section
// ============================================================================
private val LAST_TEN_WEEK_WIDTH = 34.dp
private val LAST_TEN_SCORE_WIDTH = 46.dp
private val LAST_TEN_DIFF_WIDTH = 52.dp
private val LAST_TEN_ROW_MIN_HEIGHT = 22.dp
private val LAST_TEN_OPP_MIN_WIDTH = 84.dp
private val LAST_TEN_WIN_COLOR = Color(0xFF228B22)
private val LAST_TEN_LOSS_COLOR = Color(0xFFC0392B)
private val SHARE_LAST_TEN_MIN_WIDTH = 260.dp

private fun formatLastTenDifferential(diff: Int): String =
    if (diff > 0) "+$diff" else diff.toString()

private fun formatGameLogDifferential(diff: Int?): String =
    if (diff == null) "-" else formatLastTenDifferential(diff)

private fun formatGameLogScore(score: Int?): String = score?.toString() ?: "-"

@Composable
private fun LastTenGamesRow(
    opponent: @Composable () -> Unit,
    oppScore: @Composable () -> Unit,
    teamScore: @Composable () -> Unit,
    diff: @Composable () -> Unit,
    week: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LAST_TEN_ROW_MIN_HEIGHT)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (week != null) {
            Box(
                modifier = Modifier.width(LAST_TEN_WEEK_WIDTH),
                contentAlignment = Alignment.CenterStart
            ) { week() }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = LAST_TEN_OPP_MIN_WIDTH)
                .padding(start = 4.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) { opponent() }
        Box(modifier = Modifier.width(LAST_TEN_SCORE_WIDTH), contentAlignment = Alignment.CenterEnd) { oppScore() }
        Box(modifier = Modifier.width(LAST_TEN_SCORE_WIDTH), contentAlignment = Alignment.CenterEnd) { teamScore() }
        Box(modifier = Modifier.width(LAST_TEN_DIFF_WIDTH), contentAlignment = Alignment.CenterEnd) { diff() }
    }
}

@Composable
private fun LastTenOpponentCell(game: ReportCardGameLogGame) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = game.location,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = game.opponent,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReportCardGameLogSection(
    gameLog: ReportCardGameLog,
    modifier: Modifier = Modifier
) {
    val games = gameLog.games
    if (games.isEmpty()) return

    val headerStyle = MaterialTheme.typography.labelSmall
    val headerColor = MaterialTheme.colorScheme.onSurfaceVariant
    // A full-season NFL schedule carries week numbers; the MLB last-10 ledger
    // does not, and gains nothing from an empty column.
    val showWeek = games.any { it.week != null }
    val playedCount = games.count { it.played }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(gameLog.label)

        LastTenGamesRow(
            week = if (showWeek) {
                { ReportCardHeaderText("Wk", headerStyle, headerColor, TextAlign.Start) }
            } else {
                null
            },
            opponent = { ReportCardHeaderText("Opponent", headerStyle, headerColor, TextAlign.Start) },
            oppScore = { ReportCardHeaderText("Opp", headerStyle, headerColor, TextAlign.End) },
            teamScore = { ReportCardHeaderText("Team", headerStyle, headerColor, TextAlign.End) },
            diff = { ReportCardHeaderText("Diff", headerStyle, headerColor, TextAlign.End) }
        )

        games.forEach { game ->
            LastTenGamesRow(
                week = if (showWeek) {
                    {
                        ReportCardHeaderText(
                            game.week?.toString() ?: "-",
                            MaterialTheme.typography.bodySmall,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            TextAlign.Start
                        )
                    }
                } else {
                    null
                },
                opponent = { LastTenOpponentCell(game) },
                oppScore = { ReportCardStatText(formatGameLogScore(game.opponentScore), TextAlign.End) },
                teamScore = { ReportCardStatText(formatGameLogScore(game.teamScore), TextAlign.End) },
                diff = {
                    Text(
                        text = formatGameLogDifferential(game.differential),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.End,
                        color = when {
                            !game.played -> MaterialTheme.colorScheme.onSurfaceVariant
                            game.won == true -> LAST_TEN_WIN_COLOR
                            game.won == false -> LAST_TEN_LOSS_COLOR
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Record ($playedCount games)",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = gameLog.record?.display
                    ?: "${gameLog.record?.wins ?: 0}-${gameLog.record?.losses ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Older payloads predate totalDifferential; sum the shown games instead.
        val totalDiff = gameLog.totalDifferential ?: games.sumOf { it.differential ?: 0 }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total Diff",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatLastTenDifferential(totalDiff),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    totalDiff > 0 -> LAST_TEN_WIN_COLOR
                    totalDiff < 0 -> LAST_TEN_LOSS_COLOR
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

// Scores come from the league feed (MLB Stats API / nflverse schedules), not
// from the advanced-stat providers, so credit only that one on the game log.
private fun buildReportCardGameLogShareSource(
    visualization: TeamReportCardVisualization
): String {
    val raw = visualization.source ?: ""
    val sources = parseReportCardSources(raw)
        .filter { source ->
            source.contains("MLB Stats", ignoreCase = true) ||
                source.contains("nflverse", ignoreCase = true) ||
                source.contains("nflreadr", ignoreCase = true)
        }
        .distinct()
    return sources.joinToString(" • ").ifBlank { raw.ifBlank { "League feed" } }
}

@Composable
private fun rememberLastTenShareImageWidth(
    team: ReportCardTeam,
    seasonLabel: String,
    source: String
): Dp {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val sourceStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
    val subtitleLine = formatReportCardShareSubtitleLine(seasonLabel, team)

    val subtitleWidth = with(density) {
        textMeasurer.measure(text = subtitleLine, style = titleStyle, softWrap = false, maxLines = 1).size.width.toDp()
    }
    val teamNameWidth = with(density) {
        textMeasurer.measure(text = team.teamName, style = titleStyle, softWrap = false, maxLines = 1).size.width.toDp()
    }
    val sourceWidth = with(density) {
        textMeasurer.measure(text = source, style = sourceStyle, softWrap = false, maxLines = 1).size.width.toDp()
    }
    val weekColumnWidth = if (teamGameLog(team)?.games?.any { it.week != null } == true) {
        LAST_TEN_WEEK_WIDTH
    } else {
        0.dp
    }
    val tableWidth = weekColumnWidth + LAST_TEN_OPP_MIN_WIDTH +
        LAST_TEN_SCORE_WIDTH * 2 + LAST_TEN_DIFF_WIDTH + 8.dp

    return remember(team, seasonLabel, source, subtitleWidth, teamNameWidth, sourceWidth) {
        listOf(
            tableWidth + SHARE_REPORT_CARD_HORIZONTAL_PADDING,
            subtitleWidth + SHARE_REPORT_CARD_HORIZONTAL_PADDING,
            teamNameWidth + SHARE_REPORT_CARD_HORIZONTAL_PADDING,
            sourceWidth + 72.dp + SHARE_REPORT_CARD_HORIZONTAL_PADDING
        ).max().coerceAtLeast(SHARE_LAST_TEN_MIN_WIDTH)
    }
}

@Composable
private fun TeamReportCardGameLogShareImage(
    team: ReportCardTeam,
    seasonLabel: String,
    source: String
) {
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onSurface
    val gameLog = teamGameLog(team)
    val shareWidth = rememberLastTenShareImageWidth(team, seasonLabel, source)

    Column(
        modifier = Modifier
            .width(shareWidth)
            .wrapContentHeight()
            .background(bg)
            .padding(16.dp)
    ) {
        Text(
            text = team.teamName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onBg,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatReportCardShareSubtitleLine(seasonLabel, team),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onBg,
            maxLines = 1,
            softWrap = false
        )

        Spacer(modifier = Modifier.height(12.dp))
        if (gameLog != null) {
            ReportCardGameLogSection(gameLog)
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
                color = onBg,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = "fbrk.app",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = onBg
            )
        }
    }
}

private fun formatReportCardStat(stat: ReportCardStatValue): String {
    stat.displayValue?.let { return it }
    val value = stat.value ?: return ""
    return when {
        stat.label.equals("Run Diff/G", ignoreCase = true) -> {
            val formatted = formatReportCardValue(value, 2)
            if (value >= 0) "+$formatted" else formatted
        }
        stat.label.contains("%", ignoreCase = true) -> formatReportCardValue(value, 1)
        stat.label.equals("wRC+", ignoreCase = true) -> formatReportCardValue(value, 0)
        stat.label.equals("OPS+", ignoreCase = true) -> formatReportCardValue(value, 0)
        stat.label.equals("PA", ignoreCase = true) -> formatReportCardValue(value, 0)
        stat.label.equals("Composite", ignoreCase = true) -> formatReportCardValue(value, 1)
        stat.label.equals("OAA", ignoreCase = true) || stat.label.equals("DRS", ignoreCase = true) ->
            formatReportCardValue(value, 1)
        stat.label.equals("AVG", ignoreCase = true) -> formatReportCardValue(value, 3)
        stat.label.equals("FRP", ignoreCase = true) -> formatReportCardValue(value, 0)
        stat.label.equals("SV", ignoreCase = true) -> formatReportCardValue(value, 0)
        stat.label.equals("SV/G", ignoreCase = true) -> formatReportCardValue(value, 2)
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
