package com.joebad.fastbreak.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.ui.theme.LocalColors


@Composable
fun TeamCard(
    dayOfWeek: String?,
    date: String?,
    time: String?,
    homeTeam: String?,
    homeTeamSubtitle: String?,
    awayTeam: String?,
    awayTeamSubtitle: String?,
    selectedAnswer: String? = null,
    onAnswerSelected: (String) -> Unit
) {
    val colors = LocalColors.current;
    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(3f)
        ) {
            SelectableRow(
                text = awayTeam ?: "",
                subText =  awayTeamSubtitle ?: "",
                selected = selectedAnswer == awayTeam,
                onSelect = { onAnswerSelected(awayTeam ?: "") },
                highlightColor = colors.accent
            )
            SelectableRow(
                text = homeTeam ?: "",
                subText =  homeTeamSubtitle ?: "",
                selected = selectedAnswer == homeTeam,
                onSelect = { onAnswerSelected(homeTeam ?: "") },
                highlightColor = colors.accent
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 12.dp)
        ) {
            Text(dayOfWeek ?: "", fontSize = 14.sp, color = colors.text)
            Text(
                "$date $time", style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                ),
                color = colors.text
            )
        }
    }
}


@Composable
fun SelectableRow(
    text: String,
    subText: String,
    selected: Boolean,
    onSelect: () -> Unit,
    highlightColor: Color
) {

    val animatedScale by animateFloatAsState(
        targetValue = if (selected) 1.025f else 1.0f,
        animationSpec = tween(durationMillis = 300)
    )
    val colors = LocalColors.current;

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = animatedScale,
                    scaleY = animatedScale,
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                )
                .clickable { onSelect() }
                .border(
                    width = 2.dp,
                    color = if (selected) highlightColor else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // TODO make this the state crest of the team, since logos are trademarked
//            Box(
//                modifier = Modifier
//                    .size(40.dp)
//                    .background(Color.LightGray)
//                    .clip(CircleShape),
//                contentAlignment = Alignment.Center
//            ) {
//                Icon(
//                    imageVector = Icons.Default.Person,
//                    contentDescription = "Icon",
//                    modifier = Modifier.size(24.dp)
//                )
//            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = text, style = MaterialTheme.typography.subtitle1, color = colors.text)
                Text(
                    text = subText,
                    style = MaterialTheme.typography.caption,
                    color = colors.text,
                    maxLines = 1
                )
            }
        }
    }
}
