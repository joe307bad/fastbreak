package com.joebad.fastbreak

actual fun getPlatform(): Platform = AndroidPlatform()

private class AndroidPlatform : Platform {
    override val name: String = "Android"
}

actual fun onApplicationStartPlatformSpecific() {
}
//
//actual class FontLoaderImpl(private val context: Context) : FontLoader {
//    actual override fun loadFont(fontName: String): FontFamily {
//        val resId = context.resources.getIdentifier(
//            fontName, "font", context.packageName
//        )
//        return FontFamily(
//            Font(
//                resId,
//                FontWeight.Normal,
//                FontStyle.Normal
//            )
//        )
//    }
//}