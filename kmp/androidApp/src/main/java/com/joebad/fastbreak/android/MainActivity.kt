package com.joebad.fastbreak.android

import AuthRepository
import ProfileRepository
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.joebad.fastbreak.AndroidThemePreference
import com.joebad.fastbreak.App
import com.joebad.fastbreak.Theme
import com.joebad.fastbreak.createRootComponent
import com.joebad.fastbreak.initFontLoader
import com.joebad.fastbreak.ui.theme.AppTheme
import com.joebad.fastbreak.util.AppVersion
import com.liftric.kvault.KVault
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initFontLoader(applicationContext)
        AppVersion.initialize(applicationContext)

        val kvault = KVault(applicationContext, "auth_secure_storage")
        val authRepository = AuthRepository(kvault)
        val profileRepository = ProfileRepository(authRepository)

        // TODO: Initialize database and httpClient here when dependencies are available
        val rootComponent = createRootComponent(authRepository)
        val themePreference = AndroidThemePreference(applicationContext)

        setContent {
            var theme by remember { mutableStateOf<Theme?>(null) }
            val coroutineScope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                theme = themePreference.getTheme()
            }

            AnimatedVisibility(
                visible = theme != null,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                modifier = Modifier.fillMaxSize()
            ) {
                if (theme != null) {
                    AppTheme(isDarkTheme = theme == Theme.Dark) {
                        App(rootComponent, theme)
                    }
                }
            }
        }
    }
}


