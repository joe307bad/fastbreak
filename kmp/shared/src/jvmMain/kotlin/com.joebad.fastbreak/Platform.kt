package com.joebad.fastbreak

actual fun getPlatform(): Platform = JVMPlatform()

private class JVMPlatform : Platform {
    override val name: String = "JVM"
}