package com.joebad.fastbreak

actual fun getPlatform(): Platform = AndroidPlatform()

private class AndroidPlatform : Platform {
    override val name: String = "Android"
}

actual fun onApplicationStartPlatformSpecific() {
}