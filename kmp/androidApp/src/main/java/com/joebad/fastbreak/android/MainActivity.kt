package com.joebad.fastbreak.android


import DefaultRootComponent
import RootComponent
import RootContent
import RootView
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity  // Changed this
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.essenty.lifecycle.essentyLifecycle
import com.arkivanov.decompose.extensions.android.DefaultViewContext


class MainActivity : ComponentActivity() {

    private val mode = Mode.COMPOSE

    @OptIn(ExperimentalDecomposeApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        val root =
            DefaultRootComponent(
                componentContext = defaultComponentContext(),
            )

        when (mode) {
            Mode.COMPOSE -> drawViaCompose(root)
            Mode.VIEWS -> drawViaViews(root)
        }.let {}
    }

    private fun drawViaCompose(root: RootComponent) {
        setContent {
             RootContent(component = root, modifier = Modifier.fillMaxSize())
        }
    }

    @OptIn(ExperimentalDecomposeApi::class)
    private fun drawViaViews(root: RootComponent) {
        setContentView(R.layout.main_activity)

        val viewContext =
            DefaultViewContext(
                parent = findViewById(R.id.content),
                lifecycle = essentyLifecycle(),
            )

        viewContext.apply {
            parent.addView(RootView(root))
        }
    }

    private enum class Mode {
        COMPOSE, VIEWS
    }
}
