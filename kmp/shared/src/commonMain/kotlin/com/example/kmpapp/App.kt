package com.example.kmpapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.example.kmpapp.navigation.RootComponent
import com.example.kmpapp.ui.DataVizScreen
import com.example.kmpapp.ui.HomeScreen

@Composable
fun App(rootComponent: RootComponent) {
    MaterialTheme {
        val stack by rootComponent.stack.subscribeAsState()

        Children(
            stack = stack,
            animation = stackAnimation(slide())
        ) {
            when (val child = it.instance) {
                is RootComponent.Child.Home -> HomeScreen(child.component)
                is RootComponent.Child.DataViz -> DataVizScreen(child.component)
            }
        }
    }
}
