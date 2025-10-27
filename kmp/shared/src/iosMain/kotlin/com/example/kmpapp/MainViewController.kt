package com.example.kmpapp

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.example.kmpapp.navigation.RootComponent
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val rootComponent = RootComponent(
        componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
    )

    return ComposeUIViewController {
        App(rootComponent)
    }
}
