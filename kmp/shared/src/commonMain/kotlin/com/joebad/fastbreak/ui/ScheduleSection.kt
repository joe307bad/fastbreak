package com.joebad.fastbreak.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import com.joebad.fastbreak.data.picks.ScheduleAction
import com.joebad.fastbreak.data.picks.ScheduleViewModel
import com.joebad.fastbreak.model.dtos.EmptyFastbreakCardItem
import com.joebad.fastbreak.ui.theme.AppColors
import kotlin.random.Random

@Composable
fun ScheduleSection(
    games: List<EmptyFastbreakCardItem>,
    colors: AppColors,
    viewModel: ScheduleViewModel,
    lockedCardForDate: FastbreakSelectionState? = null,
    onGameDetailsClick: (EmptyFastbreakCardItem) -> Unit = {}
) {
    val state by viewModel.container.stateFlow.collectAsState()

    // Pre-populate selections from locked card if available
    lockedCardForDate?.selections?.forEach { selection ->
        if (state.selectedWinners[selection._id] == null) {
            viewModel.handleAction(ScheduleAction.SelectWinner(selection._id, selection.userAnswer))
        }
    }

    val isLocked = lockedCardForDate?.selections?.isNotEmpty() == true

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(colors.background)
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
            GameReceiptRow(
                game = game,
                colors = colors,
                selectedWinner = state.selectedWinners[game.id],
                isLocked = isLocked,
                onWinnerSelected = { team ->
                    if (!isLocked) {
                        viewModel.handleAction(ScheduleAction.SelectWinner(game.id, team))
                    }
                },
                onGameDetailsClick = onGameDetailsClick
            )
        }
    }
}

@Composable
private fun GameReceiptRow(
    game: EmptyFastbreakCardItem,
    colors: AppColors,
    selectedWinner: String?,
    isLocked: Boolean = false,
    onWinnerSelected: (String) -> Unit,
    onGameDetailsClick: (EmptyFastbreakCardItem) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Team selection row with icons
        if (game.awayTeam != null && game.homeTeam != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Random cool icon
                RandomGameIcon(
                    gameId = game.id,
                    colors = colors
                )

                TeamSelectionButton(
                    teamName = game.awayTeam,
                    isSelected = selectedWinner == game.awayTeam,
                    isLocked = isLocked,
                    colors = colors,
                    onClick = { onWinnerSelected(game.awayTeam) },
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "@",
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace
                )

                TeamSelectionButton(
                    teamName = game.homeTeam,
                    isSelected = selectedWinner == game.homeTeam,
                    isLocked = isLocked,
                    colors = colors,
                    onClick = { onWinnerSelected(game.homeTeam) },
                    modifier = Modifier.weight(1f)
                )

                // Details icon for shared element transition
                DetailsIcon(
                    gameId = game.id,
                    colors = colors,
                    onClick = {
                        onGameDetailsClick(game)
                    }
                )
            }
        } else {
            // Game info for non-team games with icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Random cool icon
                RandomGameIcon(
                    gameId = game.id,
                    colors = colors
                )

                Text(
                    text = game.type,
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Details icon for shared element transition
                DetailsIcon(
                    gameId = game.id,
                    colors = colors,
                    onClick = {
                        onGameDetailsClick(game)
                    }
                )
            }
        }

        Divider(
            color = colors.onSurface.copy(alpha = 0.1f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun TeamSelectionButton(
    teamName: String,
    isSelected: Boolean,
    isLocked: Boolean = false,
    colors: AppColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = tween(300),
        label = "scale"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = when {
            isLocked -> if (isSelected) 1f else 0.5f
            isSelected -> 1f
            else -> 0.7f
        },
        animationSpec = tween(300),
        label = "alpha"
    )

    val animatedBackgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(300),
        label = "backgroundColor"
    )

    Text(
        text = teamName,
        style = MaterialTheme.typography.caption,
        color = when {
            isLocked && isSelected -> colors.accent
            isLocked -> colors.onSurface.copy(alpha = 0.6f)
            isSelected -> colors.accent
            else -> colors.onSurface
        },
        fontFamily = FontFamily.Monospace,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(
                color = animatedBackgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(enabled = !isLocked) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .scale(animatedScale)
            .alpha(animatedAlpha)
    )
}

@Composable
private fun RandomGameIcon(
    gameId: String,
    colors: AppColors
) {
    // Generate consistent random values based on gameId
    val randomSeed = gameId.hashCode()
    val random = remember(gameId) { Random(randomSeed) }

    // Cool icon characters that fit the monospace theme
    val coolIcons =
        listOf("◆", "◇", "●", "○", "◼", "◻", "▲", "△", "★", "☆", "■", "□", "♦", "♢", "▼", "▽")
    val iconChar = remember(gameId) { coolIcons[random.nextInt(coolIcons.size)] }

    // Cool color palette
    val coolColors = listOf(
        Color(0xFF00D2FF), // Cyan
        Color(0xFF3A5FCD), // Blue
        Color(0xFF9932CC), // Purple
        Color(0xFFFF6B35), // Orange
        Color(0xFF32CD32), // Green
        Color(0xFFFFD700), // Gold
        Color(0xFFFF69B4), // Pink
        Color(0xFF00CED1)  // Turquoise
    )
    val iconColor = remember(gameId) { coolColors[random.nextInt(coolColors.size)] }

    Row(horizontalArrangement = Arrangement.Center) {
        Column(
            modifier = Modifier
                .size(25.dp)
                .background(
                    color = iconColor.copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = iconChar,
                color = iconColor,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(0.dp).fillMaxWidth().offset(y = -1.dp)

            )
        }
    }
}

@Composable
private fun DetailsIcon(
    gameId: String,
    colors: AppColors,
    onClick: () -> Unit
) {

    Row(horizontalArrangement = Arrangement.Center) {
        Column(
            modifier = Modifier
                .size(26.dp)
                .background(
                    color = colors.accent.copy(alpha = 0.1f),
                    shape = CircleShape
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "→",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(0.dp).fillMaxSize()
                    .clickable(onClick = onClick)

            )
        }
    }
}