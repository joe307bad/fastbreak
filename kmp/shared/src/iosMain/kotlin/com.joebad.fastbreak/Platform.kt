package com.joebad.fastbreak

actual fun getPlatform(): Platform = IOSPlatform()

private class IOSPlatform : Platform {
    override val name: String = "iOS"
}

actual fun onApplicationStartPlatformSpecific() {
}