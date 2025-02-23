package com.joebad.fastbreak

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
expect fun onApplicationStartPlatformSpecific()