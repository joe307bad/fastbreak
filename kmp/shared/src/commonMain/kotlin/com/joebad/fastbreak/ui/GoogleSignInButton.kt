package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.BuildKonfig.GOOGLE_AUTH_SERVER_ID
import com.mmk.kmpauth.google.GoogleAuthCredentials
import com.mmk.kmpauth.google.GoogleAuthProvider
import com.mmk.kmpauth.google.GoogleButtonUiContainer
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun GoogleSignInButton(onLogin: (token: String?) -> Unit) {
    println("🚧 GoogleSignInButton: Using fallback button due to resource loading issue")
    
    val colors = LocalColors.current
    var authReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            GoogleAuthProvider.create(
                credentials = GoogleAuthCredentials(
                    serverId = GOOGLE_AUTH_SERVER_ID
                )
            )
            authReady = true
        } catch (e: Exception) {
            println("❌ GoogleAuth setup failed: ${e.message}")
        }
    }

    MaterialTheme {
        if (authReady) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Custom button implementation to avoid resource loading crash
                GoogleButtonUiContainer(
                    onGoogleSignInResult = { googleUser ->
                        onLogin(googleUser?.idToken)
                    }
                ) {
                    // Use PhysicalButton to match app theme
                    PhysicalButton(
                        onClick = { this.onClick() },
                        modifier = Modifier.widthIn(max = 240.dp),
                        backgroundColor = colors.secondary,
                        contentColor = colors.onSecondary,
                        bottomBorderColor = colors.accent
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SIGN IN",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}