package com.joebad.fastbreak.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FastbreakLogo(isDark: Boolean) {
    val imageName = if (isDark) "fastbreak_logo" else "fastbreak_logo_light"
    
    UIKitView(
        factory = {
            val imageView = UIImageView()
            val image = UIImage.imageNamed(imageName)
            if (image != null) {
                imageView.image = image
                imageView.contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
            }
            imageView
        },
        modifier = Modifier.fillMaxSize()
    )
}