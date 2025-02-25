package com.joebad.fastbreak

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    App(rootComponent = createRootComponent())
}