package com.joebad.fastbreak.util

import platform.Foundation.NSBundle

actual object AppVersion {
    actual fun getVersionName(): String {
        val bundle = NSBundle.mainBundle
        return bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "Unknown"
    }
    
    actual fun getVersionCode(): Int {
        val bundle = NSBundle.mainBundle
        val versionString = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String
        return versionString?.toIntOrNull() ?: 0
    }
}