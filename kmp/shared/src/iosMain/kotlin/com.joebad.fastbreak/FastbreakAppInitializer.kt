package com.joebad.fastbreak

import com.mmk.kmpauth.google.GoogleAuthCredentials
import com.mmk.kmpauth.google.GoogleAuthProvider

object FastbreakAppInitializer {
    fun onApplicationStart() {
        GoogleAuthProvider.create(credentials = GoogleAuthCredentials(serverId = BuildKonfig.GOOGLE_AUTH_SERVER_ID))
    }
}