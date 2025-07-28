package com.joebad.fastbreak.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.CardWithBadge
import com.joebad.fastbreak.ui.shimmerEffect
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.ui.theme.lighten


@Composable
fun LoadingDailyFastbreak() {
    val colors = LocalColors.current;
    CardWithBadge(
        badgeText = "LOADING THE DAILY FASTBREAK",
        modifier = Modifier.padding(bottom = 30.dp),
        content = {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(3f)
                ) {
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .fillMaxWidth()
                            .background(colors.primary)
                            .shimmerEffect(
                                baseColor = lighten(colors.primary, 0.6f),
                                highlightColor = lighten(colors.primary, 0.4f),
                                durationMillis = 2000
                            )
                    )
                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .fillMaxWidth()
                            .background(colors.primary)
                            .shimmerEffect(
                                baseColor = lighten(colors.primary, 0.6f),
                                highlightColor = lighten(colors.primary, 0.4f),
                                durationMillis = 2000
                            )
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .fillMaxWidth()
                            .background(colors.primary)
                            .shimmerEffect(
                                baseColor = lighten(colors.primary, 0.6f),
                                highlightColor = lighten(colors.primary, 0.4f),
                                durationMillis = 2000
                            )
                    )
                }
            }
        },
        badgeColor = colors.secondary,
        badgeTextColor = colors.onSecondary,
        points = ""
    )
}