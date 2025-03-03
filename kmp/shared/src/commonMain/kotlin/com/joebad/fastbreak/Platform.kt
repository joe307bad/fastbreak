package com.joebad.fastbreak

interface Platform {
    val name: String
}

//interface FontLoader {
//    fun loadFont(fontName: String): FontFamily
//}

expect fun getPlatform(): Platform
expect fun onApplicationStartPlatformSpecific()

//expect class FontLoaderImpl() : FontLoader