package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmk.kmpauth.google.GoogleButtonUiContainer
import kotlinx.serialization.Serializable

@Serializable
data class User (
    val id_token: String
)

@Composable
fun App() {

    MaterialTheme {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {

            var signedInUserName: String by remember { mutableStateOf("") }
            val onFirebaseResult: (Result<User?>) -> Unit = { result ->
                if (result.isSuccess) {
                    val firebaseUser = result.getOrNull()
                    signedInUserName =
                        firebaseUser?.id_token ?: firebaseUser?.id_token ?: "Null User"
                } else {
                    signedInUserName = "Null User"
                    println("Error Result: ${result.exceptionOrNull()?.message}")
                }

            }
            Text(
                text = signedInUserName,
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Start,
            )

            //Google Sign-In with Custom Button and authentication without Firebase
            GoogleButtonUiContainer(onGoogleSignInResult = { googleUser ->
                val idToken = googleUser?.idToken // Send this idToken to your backend to verify
                signedInUserName = googleUser?.displayName ?: "Null User"
            }) {
                Button(onClick = { this.onClick() }) { Text("Google Sign-In(Custom Design)") }
            }


        }
    }
}