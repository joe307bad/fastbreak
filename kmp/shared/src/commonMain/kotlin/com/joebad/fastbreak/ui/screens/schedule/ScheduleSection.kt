package com.joebad.fastbreak.ui.screens.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.picks.ScheduleAction
import com.joebad.fastbreak.data.picks.ScheduleViewModel
import com.joebad.fastbreak.model.dtos.EmptyFastbreakCardItem
import com.joebad.fastbreak.ui.theme.AppColors

@Composable
fun ScheduleSection(
    games: List<EmptyFastbreakCardItem>, 
    colors: AppColors,
    viewModel: ScheduleViewModel
) {
    val state by viewModel.container.stateFlow.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(vertical = 2.dp)
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
                onWinnerSelected = { team -> 
                    viewModel.handleAction(ScheduleAction.SelectWinner(game.id, team))
                }
            )
        }
    }
}

@Composable
private fun GameReceiptRow(
    game: EmptyFastbreakCardItem, 
    colors: AppColors,
    selectedWinner: String?,
    onWinnerSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Team selection row
        if (game.awayTeam != null && game.homeTeam != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamSelectionButton(
                    teamName = game.awayTeam,
                    isSelected = selectedWinner == game.awayTeam,
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
                    colors = colors,
                    onClick = { onWinnerSelected(game.homeTeam) },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Game info for non-team games
            Text(
                text = game.type,
                style = MaterialTheme.typography.caption,
                color = colors.onSurface,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        targetValue = if (isSelected) 1f else 0.7f,
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
        color = if (isSelected) colors.accent else colors.onSurface,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(
                color = animatedBackgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .scale(animatedScale)
            .alpha(animatedAlpha)
    )
}