package com.joebad.fastbreak.ui.screens

import AuthedUser
import androidx.compose.runtime.Composable
import com.appstractive.jwt.JWT
import com.appstractive.jwt.from
import com.joebad.fastbreak.ui.GoogleSignInButton

@Composable
fun LoginScreen(goToHome: (user: AuthedUser) -> Unit) {
    GoogleSignInButton(
        onLogin = { token ->
            if (token != null) {
                val jwt = JWT.from(token)
                val email = jwt.claims["email"].toString();
                val exp = jwt.claims.get("exp").toString().toLong();
                println("Decoded payload: ${jwt.header}")
                goToHome(
                    AuthedUser(
                        email,
                        exp,
                        token
                    )
                )
            }
        }
    )
}