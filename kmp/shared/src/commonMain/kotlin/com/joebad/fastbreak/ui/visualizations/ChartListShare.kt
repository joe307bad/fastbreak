package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.BarGraphDataPoint
import com.joebad.fastbreak.data.model.BarGraphVisualization
import com.joebad.fastbreak.data.model.LineChartVisualization
import com.joebad.fastbreak.data.model.QuadrantConfig
import com.joebad.fastbreak.data.model.ScatterPlotDataPoint
import com.joebad.fastbreak.data.model.ScatterPlotVisualization
import com.joebad.fastbreak.data.model.TableVisualization
import com.joebad.fastbreak.data.model.VisualizationType
import com.joebad.fastbreak.platform.getImageExporter
import kotlin.math.max
import kotlin.math.roundToInt

// ============================================================================
// Shared formatting helpers (used by both the on-screen table and share images)
// ============================================================================

/** Format a double to a specific number of decimal places. */
private fun Double.formatTo(decimals: Int): String {
    val multiplier = when (decimals) {
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
    val rounded = (this * multiplier).roundToInt() / multiplier
    return rounded.toString()
}

/** Format ordinal numbers (1st, 2nd, 3rd, etc.) */
private fun formatOrdinal(number: Int): String {
    return when {
        number % 100 in 11..13 -> "${number}th"
        number % 10 == 1 -> "${number}st"
        number % 10 == 2 -> "${number}nd"
        number % 10 == 3 -> "${number}rd"
        else -> "${number}th"
    }
}

/** Format a conference ranking (e.g., "6th / East") */
private fun formatConferenceRank(rank: Int?, conference: String?): String {
    if (rank == null) return "-"
    val confName = when (conference?.lowercase()) {
        "east", "eastern" -> "East"
        "west", "western" -> "West"
        else -> conference ?: "Conf"
    }
    return "${formatOrdinal(rank)} / $confName"
}

/** Parse a hex color string (e.g. "#2196F3") into a Compose Color. */
private fun parseHexColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    return Color(("FF$cleanHex").toLong(16))
}

/** Win percentage cell text for a data point that may not carry a record. */
private fun formatWinPct(wins: Int?, losses: Int?): String {
    if (wins == null || losses == null) return "-"
    val totalGames = wins + losses
    return if (totalGames > 0) (wins.toDouble() / totalGames * 100).formatTo(1) else "-"
}

// ============================================================================
// Hoisted sort state
// ============================================================================

/**
 * Sort state for a chart's data table, hoisted so the share images can render
 * the list in exactly the order the user is currently looking at.
 *
 * A null [column] means the table's default sort (score / value, descending).
 */
class ChartTableSortState {
    var column by mutableStateOf<String?>(null)
    var ascending by mutableStateOf(false)
}

@Composable
fun rememberChartTableSortState(): ChartTableSortState = remember { ChartTableSortState() }

// ============================================================================
// Shared sorting / ranking
// ============================================================================

internal fun scatterScoreRanks(
    data: List<ScatterPlotDataPoint>,
    invertYAxis: Boolean
): Map<ScatterPlotDataPoint, Int> {
    val sortedByScore = if (invertYAxis) {
        data.sortedByDescending { it.x - it.y }
    } else {
        data.sortedByDescending { it.sum }
    }
    return sortedByScore.mapIndexed { index, point -> point to (index + 1) }.toMap()
}

internal fun sortScatterPoints(
    data: List<ScatterPlotDataPoint>,
    sortColumn: String?,
    sortAscending: Boolean,
    invertYAxis: Boolean
): List<ScatterPlotDataPoint> = when (sortColumn) {
    "label" -> if (sortAscending) data.sortedBy { it.label } else data.sortedByDescending { it.label }
    "winPct" -> if (sortAscending) {
        data.sortedBy { winPctOrZero(it.wins, it.losses) }
    } else {
        data.sortedByDescending { winPctOrZero(it.wins, it.losses) }
    }
    "confRank" -> if (sortAscending) {
        data.sortedBy { it.conferenceRank ?: Int.MAX_VALUE }
    } else {
        data.sortedByDescending { it.conferenceRank ?: Int.MIN_VALUE }
    }
    "team" -> if (sortAscending) data.sortedBy { it.teamCode ?: "" } else data.sortedByDescending { it.teamCode ?: "" }
    "x" -> if (sortAscending) data.sortedBy { it.x } else data.sortedByDescending { it.x }
    "y" -> if (sortAscending) data.sortedBy { it.y } else data.sortedByDescending { it.y }
    "score", null -> if (invertYAxis) {
        if (sortAscending) data.sortedBy { it.x - it.y } else data.sortedByDescending { it.x - it.y }
    } else {
        if (sortAscending) data.sortedBy { it.sum } else data.sortedByDescending { it.sum }
    }
    else -> data
}

internal fun barValueRanks(data: List<BarGraphDataPoint>): Map<BarGraphDataPoint, Int> =
    data.sortedByDescending { it.value }
        .mapIndexed { index, point -> point to (index + 1) }
        .toMap()

internal fun sortBarPoints(
    data: List<BarGraphDataPoint>,
    sortColumn: String?,
    sortAscending: Boolean
): List<BarGraphDataPoint> = when (sortColumn) {
    "label" -> if (sortAscending) data.sortedBy { it.label } else data.sortedByDescending { it.label }
    "winPct" -> if (sortAscending) {
        data.sortedBy { winPctOrZero(it.wins, it.losses) }
    } else {
        data.sortedByDescending { winPctOrZero(it.wins, it.losses) }
    }
    "confRank" -> if (sortAscending) {
        data.sortedBy { it.conferenceRank ?: Int.MAX_VALUE }
    } else {
        data.sortedByDescending { it.conferenceRank ?: Int.MIN_VALUE }
    }
    "value", null -> if (sortAscending) data.sortedBy { it.value } else data.sortedByDescending { it.value }
    else -> data
}

private fun winPctOrZero(wins: Int?, losses: Int?): Double {
    if (wins == null || losses == null) return 0.0
    val total = wins + losses
    return if (total > 0) wins.toDouble() / total else 0.0
}

/** Row indices the line chart table samples, mirroring the on-screen table. */
internal fun lineChartSampledIndices(maxPoints: Int): List<Int> =
    if (maxPoints > 20) {
        val step = maxPoints / 20
        (0 until maxPoints step step).toList()
    } else {
        (0 until maxPoints).toList()
    }

// ============================================================================
// List model
// ============================================================================

data class ChartListColumn(val label: String)

data class ChartListRow(
    val cells: List<String>,
    /** Optional dot color rendered before the label cell (scatter quadrant color). */
    val accentColor: Color? = null
)

data class ChartListTable(
    val columns: List<ChartListColumn>,
    val rows: List<ChartListRow>,
    /** Index of the column the accent dot belongs to, or -1 when there is none. */
    val accentColumnIndex: Int = -1
)

/**
 * Build a flat, shareable representation of the list a chart shows underneath
 * itself, honoring the sort the user currently has applied.
 *
 * Returns null for visualization types that don't render a generic list.
 */
fun buildChartListTable(
    visualization: VisualizationType,
    sortColumn: String?,
    sortAscending: Boolean
): ChartListTable? = when (visualization) {
    is ScatterPlotVisualization -> buildScatterListTable(visualization, sortColumn, sortAscending)
    is BarGraphVisualization -> buildBarListTable(visualization, sortColumn, sortAscending)
    is LineChartVisualization -> buildLineListTable(visualization)
    is TableVisualization -> buildGenericListTable(visualization)
    else -> null
}

private fun quadrantColorFor(
    point: ScatterPlotDataPoint,
    avgX: Double,
    avgY: Double,
    invertYAxis: Boolean,
    topRight: QuadrantConfig?,
    topLeft: QuadrantConfig?,
    bottomLeft: QuadrantConfig?,
    bottomRight: QuadrantConfig?
): Color {
    val isGoodY = if (invertYAxis) point.y < avgY else point.y >= avgY
    return when {
        point.x >= avgX && isGoodY -> topRight?.let { parseHexColor(it.color) } ?: Color(0xFF4CAF50)
        point.x < avgX && isGoodY -> topLeft?.let { parseHexColor(it.color) } ?: Color(0xFF2196F3)
        point.x < avgX && !isGoodY -> bottomLeft?.let { parseHexColor(it.color) } ?: Color(0xFFFF9800)
        else -> bottomRight?.let { parseHexColor(it.color) } ?: Color(0xFFF44336)
    }
}

private fun buildScatterListTable(
    visualization: ScatterPlotVisualization,
    sortColumn: String?,
    sortAscending: Boolean
): ChartListTable? {
    val data = visualization.dataPoints
    if (data.isEmpty()) return null

    val invertYAxis = visualization.invertYAxis
    val hasRecords = data.any { it.wins != null && it.losses != null }
    val hasConfRanks = data.any { it.conferenceRank != null }
    val hasTeamCodes = data.any { it.teamCode != null }

    val columns = buildList {
        add(ChartListColumn("Rank"))
        add(ChartListColumn("Player/Team"))
        if (hasRecords) add(ChartListColumn("Win%"))
        if (hasConfRanks) add(ChartListColumn("Conf Rank"))
        if (hasTeamCodes) add(ChartListColumn("Team"))
        add(ChartListColumn(visualization.xColumnLabel ?: "X Value"))
        add(ChartListColumn(visualization.yColumnLabel ?: "Y Value"))
        add(ChartListColumn("Score"))
    }

    val avgX = data.map { it.x }.average()
    val avgY = data.map { it.y }.average()
    val rankMap = scatterScoreRanks(data, invertYAxis)

    val rows = sortScatterPoints(data, sortColumn, sortAscending, invertYAxis).map { point ->
        val score = if (invertYAxis) point.x - point.y else point.sum
        ChartListRow(
            cells = buildList {
                add((rankMap[point] ?: 0).toString())
                add(point.label)
                if (hasRecords) add(formatWinPct(point.wins, point.losses))
                if (hasConfRanks) add(formatConferenceRank(point.conferenceRank, point.conference))
                if (hasTeamCodes) add(point.teamCode ?: "-")
                add(point.x.formatTo(2))
                add(point.y.formatTo(2))
                add(score.formatTo(2))
            },
            accentColor = quadrantColorFor(
                point, avgX, avgY, invertYAxis,
                visualization.quadrantTopRight,
                visualization.quadrantTopLeft,
                visualization.quadrantBottomLeft,
                visualization.quadrantBottomRight
            )
        )
    }

    return ChartListTable(columns = columns, rows = rows, accentColumnIndex = 1)
}

private fun buildBarListTable(
    visualization: BarGraphVisualization,
    sortColumn: String?,
    sortAscending: Boolean
): ChartListTable? {
    val data = visualization.dataPoints
    if (data.isEmpty()) return null

    val hasRecords = data.any { it.wins != null && it.losses != null }
    val hasConfRanks = data.any { it.conferenceRank != null }

    val columns = buildList {
        add(ChartListColumn("Rank"))
        add(ChartListColumn("Team"))
        if (hasRecords) add(ChartListColumn("Win%"))
        if (hasConfRanks) add(ChartListColumn("Conf Rank"))
        add(ChartListColumn("Value"))
    }

    val rankMap = barValueRanks(data)
    val rows = sortBarPoints(data, sortColumn, sortAscending).map { point ->
        ChartListRow(
            cells = buildList {
                add((rankMap[point] ?: 0).toString())
                add(point.label)
                if (hasRecords) add(formatWinPct(point.wins, point.losses))
                if (hasConfRanks) add(formatConferenceRank(point.conferenceRank, point.conference))
                add(point.value.formatTo(1))
            }
        )
    }

    return ChartListTable(columns = columns, rows = rows)
}

private fun buildLineListTable(visualization: LineChartVisualization): ChartListTable? {
    val series = visualization.series
    if (series.isEmpty()) return null

    val columns = buildList {
        add(ChartListColumn("Week/Game"))
        series.forEach { add(ChartListColumn(it.label)) }
    }

    val maxPoints = series.maxOfOrNull { it.dataPoints.size } ?: 0
    val rows = lineChartSampledIndices(maxPoints).map { pointIndex ->
        ChartListRow(
            cells = buildList {
                val xValue = series.firstOrNull()?.dataPoints?.getOrNull(pointIndex)?.x?.toInt() ?: pointIndex
                add(xValue.toString())
                series.forEach { s ->
                    add(s.dataPoints.getOrNull(pointIndex)?.y?.formatTo(1) ?: "-")
                }
            }
        )
    }

    return if (rows.isEmpty()) null else ChartListTable(columns = columns, rows = rows)
}

private fun buildGenericListTable(visualization: TableVisualization): ChartListTable? {
    val data = visualization.dataPoints
    if (data.isEmpty()) return null

    val columnLabels = data.first().columns.map { it.label }
    val columns = buildList {
        add(ChartListColumn("Team"))
        columnLabels.forEach { add(ChartListColumn(it)) }
    }

    val rows = data.map { point ->
        ChartListRow(
            cells = buildList {
                add(point.label)
                columnLabels.forEachIndexed { index, label ->
                    val cell = point.columns.getOrNull(index)
                        ?.takeIf { it.label == label }
                        ?: point.columns.firstOrNull { it.label == label }
                    add(cell?.value ?: "-")
                }
            }
        )
    }

    return ChartListTable(columns = columns, rows = rows)
}

// ============================================================================
// Share scopes
// ============================================================================

enum class ChartListShareScope(val fabLabel: String) {
    FULL("Share whole list"),
    TOP_HALF("Share top half"),
    BOTTOM_HALF("Share bottom half"),
    TOP_TEN("Share top 10")
}

/** Halves are pointless on tiny lists, and "top 10" only when there's more than 10. */
fun ChartListShareScope.isAvailableFor(rowCount: Int): Boolean = when (this) {
    ChartListShareScope.FULL -> rowCount > 0
    ChartListShareScope.TOP_HALF, ChartListShareScope.BOTTOM_HALF -> rowCount >= 4
    ChartListShareScope.TOP_TEN -> rowCount > 10
}

private fun topHalfCount(rowCount: Int): Int = (rowCount + 1) / 2

fun ChartListTable.sliceFor(scope: ChartListShareScope): ChartListTable = when (scope) {
    ChartListShareScope.FULL -> this
    ChartListShareScope.TOP_HALF -> copy(rows = rows.take(topHalfCount(rows.size)))
    ChartListShareScope.BOTTOM_HALF -> copy(rows = rows.drop(topHalfCount(rows.size)))
    ChartListShareScope.TOP_TEN -> copy(rows = rows.take(10))
}

/** Human-readable description of the slice, rendered under the chart title. */
fun ChartListShareScope.subtitleFor(rowCount: Int): String = when (this) {
    ChartListShareScope.FULL -> "All $rowCount"
    ChartListShareScope.TOP_HALF -> "Top ${topHalfCount(rowCount)} of $rowCount"
    ChartListShareScope.BOTTOM_HALF -> "Bottom ${rowCount - topHalfCount(rowCount)} of $rowCount"
    ChartListShareScope.TOP_TEN -> "Top 10 of $rowCount"
}

// ============================================================================
// Share image
// ============================================================================

private val COLUMN_SPACING = 12.dp
private val SHARE_PADDING = 16.dp
private val ACCENT_DOT_SPACE = 16.dp

/**
 * Renders a chart's list as a standalone image, sized to exactly fit the
 * columns that list happens to have.
 */
@Composable
fun ChartListShareImage(
    title: String,
    subtitle: String,
    source: String?,
    table: ChartListTable
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val backgroundColor = MaterialTheme.colorScheme.background
    val onBackground = MaterialTheme.colorScheme.onBackground
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline

    val headerStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    val cellStyle = MaterialTheme.typography.bodySmall
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val subtitleStyle = MaterialTheme.typography.labelMedium
    val footerStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)

    fun measure(text: String, style: TextStyle): Dp = with(density) {
        textMeasurer.measure(text = text, style = style, softWrap = false, maxLines = 1)
            .size.width.toDp()
    }

    // Measuring every cell is the whole point (columns fit their content), but it
    // is also the expensive part - only redo it when the list itself changes.
    val (columnWidths, contentWidth) = remember(table, title, subtitle, source, density) {
        val widths: List<Dp> = table.columns.mapIndexed { index, column ->
            val headerWidth = measure(column.label, headerStyle)
            val widestCell = table.rows
                .map { measure(it.cells.getOrElse(index) { "" }, cellStyle) }
                .maxOrNull() ?: 0.dp
            val accentSpace = if (index == table.accentColumnIndex) ACCENT_DOT_SPACE else 0.dp
            max(headerWidth.value, widestCell.value).dp + accentSpace + 4.dp
        }
        val tableWidth = widths.fold(0.dp) { acc, w -> acc + w } +
            COLUMN_SPACING * (table.columns.size - 1).coerceAtLeast(0)
        val footerWidth = measure(source.orEmpty(), footerStyle) + 72.dp
        widths to listOf(
            tableWidth,
            measure(title, titleStyle),
            measure(subtitle, subtitleStyle),
            footerWidth
        ).maxOf { it.value }.dp
    }

    Column(
        modifier = Modifier
            .width(contentWidth + SHARE_PADDING * 2)
            .wrapContentHeight()
            .background(backgroundColor)
            .padding(SHARE_PADDING)
    ) {
        Text(
            text = title,
            style = titleStyle,
            color = onBackground,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = subtitleStyle,
            color = valueColor,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_SPACING)) {
            table.columns.forEachIndexed { index, column ->
                Text(
                    text = column.label,
                    style = headerStyle,
                    color = onBackground,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(columnWidths[index])
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = dividerColor, thickness = 1.dp)

        table.rows.forEach { row ->
            Row(
                modifier = Modifier.padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(COLUMN_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                table.columns.forEachIndexed { index, _ ->
                    val text = row.cells.getOrElse(index) { "" }
                    if (index == table.accentColumnIndex && row.accentColor != null) {
                        Row(
                            modifier = Modifier.width(columnWidths[index]),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(row.accentColor, CircleShape)
                            )
                            Text(
                                text = text,
                                style = cellStyle,
                                color = valueColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Text(
                            text = text,
                            style = cellStyle,
                            color = valueColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(columnWidths[index])
                        )
                    }
                }
            }
            HorizontalDivider(color = dividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.width(contentWidth),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = source?.takeIf { it.isNotBlank() }?.let { "Data source: $it" } ?: "",
                style = footerStyle,
                color = onBackground,
                maxLines = 1
            )
            Text(
                text = "fbrk.app",
                style = footerStyle,
                fontWeight = FontWeight.Bold,
                color = onBackground,
                maxLines = 1
            )
        }
    }
}

/**
 * Renders [ChartListShareImage] off-screen, captures it, and hands it to the
 * platform share sheet. Calls [onFinished] once the share has been triggered.
 */
@Composable
fun ChartListShareCapture(
    title: String,
    subtitle: String,
    source: String?,
    table: ChartListTable,
    shareTitle: String,
    onFinished: () -> Unit
) {
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }

    LaunchedEffect(table, subtitle) {
        kotlinx.coroutines.delay(50)
        try {
            imageExporter.shareImage(graphicsLayer.toImageBitmap(), shareTitle)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            onFinished()
        }
    }

    // Long lists would otherwise produce a bitmap too tall for the GPU to back,
    // so trade pixel density for height once the list gets big.
    val estimatedHeightDp = 140f + table.rows.size * 26f
    val captureScale = (4000f / estimatedHeightDp).coerceIn(1f, 2f)

    CompositionLocalProvider(LocalDensity provides Density(captureScale, 1f)) {
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
            ChartListShareImage(
                title = title,
                subtitle = subtitle,
                source = source,
                table = table
            )
        }
    }
}
