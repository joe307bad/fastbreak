package com.joebad.fastbreak.util

expect object AppVersion {
    fun getVersionName(): String
    fun getVersionCode(): Int
}