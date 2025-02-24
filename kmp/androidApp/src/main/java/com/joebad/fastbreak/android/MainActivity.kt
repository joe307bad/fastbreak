package com.joebad.fastbreak.android

import AndroidAppRoot
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.joebad.fastbreak.ui.AppInitializer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppInitializer.onApplicationStart()

        setContent {
            AndroidAppRoot()
        }
    }
}