package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.joebad.fastbreak.BuildKonfig.GOOGLE_AUTH_SERVER_ID
import com.mmk.kmpauth.google.GoogleAuthCredentials
import com.mmk.kmpauth.google.GoogleAuthProvider
import com.mmk.kmpauth.google.GoogleButtonUiContainer
import com.mmk.kmpauth.uihelper.google.GoogleSignInButton
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun GoogleSignInButton(onLogin: (token: String?) -> Unit) {
    var authReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {

        GoogleAuthProvider.create(
            credentials = GoogleAuthCredentials(
                serverId = GOOGLE_AUTH_SERVER_ID
            )
        )
        authReady = true
    }

    MaterialTheme {
        if (authReady) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                GoogleButtonUiContainer(
                    onGoogleSignInResult = { googleUser ->
                        onLogin(googleUser?.idToken)
                    }
                ) {
                    GoogleSignInButton(
                        onClick = { this.onClick() }
                    )
                }
            }
        }
    }
}