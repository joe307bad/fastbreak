package com.joebad.fastbreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Card
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.Star

@Composable
fun ThreeSurfacesInRow(
    firstText: String,
    thirdText: String,
    badgeTextColor: Color?,
    badgeColor: Color?
) {
    val colors = LocalColors.current;
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = badgeColor ?: colors.primary,
            modifier = Modifier
                .fillMaxWidth().weight(1f)
        ) {
            Text(
                text = firstText,
                color = badgeTextColor ?: colors.onPrimary,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
            )
        }
        Surface(
            color = badgeColor ?: colors.primary,
            modifier = Modifier
                .wrapContentWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CupertinoIcon(
                    imageVector = CupertinoIcons.Filled.Star,
                    contentDescription = "Star",
                    tint = badgeTextColor ?: colors.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = thirdText,
                    color = badgeTextColor ?: colors.onPrimary,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }
        }
    }

}

@Composable
fun CardWithBadge(
    badgeText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    badgeColor: Color? = null,
    badgeTextColor: Color? = null,
    points: String = "100"
) {
    val colors = LocalColors.current;
    Card(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.background),
        elevation = 4.dp,
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().background(color = colors.background)
        ) {
            ThreeSurfacesInRow(
                badgeText,
                points,
                badgeTextColor,
                badgeColor,
            )
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }
    }
}
