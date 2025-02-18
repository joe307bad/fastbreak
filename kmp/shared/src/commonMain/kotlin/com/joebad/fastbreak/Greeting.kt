package com.joebad.fastbreak


class Greeting {
    private val platform = object : Platform {
        override val name: String = "Custom OS"
    }

    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}


