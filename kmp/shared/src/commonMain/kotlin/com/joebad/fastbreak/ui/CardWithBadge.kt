package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.Star

@Composable
fun CardWithBadge(
    badgeText: String,
    dateText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(bottom = 10.dp),
        elevation = 4.dp,
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // First surface - left justified, fills available space
                Surface(
                    color = Color(0xFF8A2BE2),
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = "${badgeText} | Mon. Nov. 12",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Second surface - right justified, only takes needed width
                Surface(
                    color = MaterialTheme.colors.primaryVariant,
                    modifier = Modifier
                        .wrapContentWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        CupertinoIcon(
                            imageVector = CupertinoIcons.Filled.Star,
                            contentDescription = "Star",
                            tint = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "100",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun PillBadge(
    text: String,
    backgroundColor: Color,
    iconRes: @Composable (() -> Unit)? = null
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Row(
//            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
//            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconRes != null) {
                iconRes()
            }
            Text(
                text = text,
                color = MaterialTheme.colors.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
