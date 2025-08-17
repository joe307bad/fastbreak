package com.joebad.fastbreak.utils

expect object AppVersion {
    fun getVersionName(): String
    fun getVersionCode(): Int
}