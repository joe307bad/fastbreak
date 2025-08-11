package com.joebad.fastbreak.ui.screens

import AuthedUser
import com.joebad.fastbreak.ui.FastbreakLogo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appstractive.jwt.JWT
import com.appstractive.jwt.from
import com.joebad.fastbreak.Theme
import com.joebad.fastbreak.ui.GoogleSignInButton
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun LoginScreen(goToHome: (user: AuthedUser) -> Unit, theme: Theme?, error: String? = null, isLoading: Boolean = false) {
    val colors = LocalColors.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(200.dp)) {
            FastbreakLogo(isDark = theme == Theme.Dark)
        }
        Box(modifier = Modifier.height(100.dp)) {
            GoogleSignInButton(
                onLogin = { token ->
                    if (token != null) {
                        val jwt = JWT.from(token)
                        val email = jwt.claims["email"].toString();
                        val exp = jwt.claims.get("exp").toString().toLong();
                        val sub = jwt.claims["sub"].toString().replace("\"", "")
                        println("Decoded payload: ${jwt.header}")
                        goToHome(
                            AuthedUser(
                                email,
                                exp,
                                token,
                                userId = sub,
                                userName = ""
                            )
                        )
                    }
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = colors.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                error != null -> {
                    Text(
                        text = error,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}