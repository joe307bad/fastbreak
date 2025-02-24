package com.joebad.fastbreak.ui

import com.joebad.fastbreak.BuildKonfig.GOOGLE_AUTH_SERVER_ID
import com.joebad.fastbreak.onApplicationStartPlatformSpecific
import com.mmk.kmpauth.google.GoogleAuthCredentials
import com.mmk.kmpauth.google.GoogleAuthProvider


object AppInitializer {
    fun onApplicationStart() {
        onApplicationStartPlatformSpecific()
        GoogleAuthProvider.create(credentials = GoogleAuthCredentials(serverId = GOOGLE_AUTH_SERVER_ID))
    }
}