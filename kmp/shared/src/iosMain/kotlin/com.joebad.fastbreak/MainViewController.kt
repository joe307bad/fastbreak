package com.joebad.fastbreak

import AuthRepository
import ProfileRepository
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.joebad.fastbreak.ui.theme.AppTheme
import com.liftric.kvault.KVault
import kotlinx.coroutines.launch

fun MainViewController() = ComposeUIViewController {
    var theme by remember { mutableStateOf<Theme?>(null) }
    val fontLoader = createFontLoader()
    fontLoader.loadFont("CodeBold.otf")

    val themePreference = IosThemePreference()
    val coroutineScope = rememberCoroutineScope()

    // KVault uses iOS Keychain which persists data until explicitly deleted
    val kvault = KVault("auth_secure_storage")
    val authRepository = AuthRepository(kvault)
    val profileRepository = ProfileRepository(authRepository)

    LaunchedEffect(Unit) {
        theme = themePreference.getTheme()
    }

    AppTheme(isDarkTheme = theme == Theme.Dark) {
        App(
            rootComponent = createRootComponent(authRepository),
            onToggleTheme = { selectedTheme ->
                theme = selectedTheme
                coroutineScope.launch {
                    themePreference.saveTheme(selectedTheme)
                }
            },
            themePreference = themePreference,
            authRepository = authRepository,
            profileRepository = profileRepository,
            theme = theme
        )
    }
}
