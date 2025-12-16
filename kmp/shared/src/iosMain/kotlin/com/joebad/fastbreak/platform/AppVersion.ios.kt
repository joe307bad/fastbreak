package com.joebad.fastbreak.platform

import platform.Foundation.NSBundle

actual object AppVersion {
    actual val versionName: String
        get() = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "unknown"

    actual val buildNumber: String
        get() = NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String ?: "0"
}
