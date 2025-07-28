package com.joebad.fastbreak.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.CardWithBadge
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun ErrorDailyFastbreak(error: String) {
    val colors = LocalColors.current;
    CardWithBadge(
        badgeText = "ERROR",
        modifier = Modifier.padding(bottom = 30.dp),
        content = {
            Text(
                text = "Failed to load Daily Fastbreak: $error",
                color = colors.onPrimary,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
            )
        },
        badgeColor = colors.error,
        badgeTextColor = colors.onError,
        points = ""
    )
}