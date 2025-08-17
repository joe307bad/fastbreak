package com.joebad.fastbreak.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fastbreak.shared.generated.resources.Res
import fastbreak.shared.generated.resources.fastbreak_logo
import fastbreak.shared.generated.resources.fastbreak_logo_light
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun FastbreakLogo(isDark: Boolean) {
    val logoResource = if (isDark) Res.drawable.fastbreak_logo else Res.drawable.fastbreak_logo_light
    
    Image(
        painter = painterResource(logoResource),
        contentDescription = "Fastbreak Logo"
    )
}