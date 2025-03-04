package com.joebad.fastbreak.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.joebad.fastbreak.App
import com.joebad.fastbreak.createRootComponent
import com.joebad.fastbreak.initFontLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initFontLoader(applicationContext)

        val rootComponent = createRootComponent()

        setContent {
            App(rootComponent = rootComponent)
        }
    }
}