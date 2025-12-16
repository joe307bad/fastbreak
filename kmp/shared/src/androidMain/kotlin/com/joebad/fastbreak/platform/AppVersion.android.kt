package com.joebad.fastbreak.platform

import android.content.Context
import android.content.pm.PackageManager

actual object AppVersion {
    private var _versionName: String = "unknown"
    private var _buildNumber: String = "0"

    actual val versionName: String
        get() = _versionName

    actual val buildNumber: String
        get() = _buildNumber

    fun init(context: Context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            _versionName = packageInfo.versionName ?: "unknown"
            _buildNumber = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // Keep defaults
        }
    }
}
