package com.joebad.fastbreak.utils

import android.content.Context
import android.content.pm.PackageManager

actual object AppVersion {
    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context
    }

    actual fun getVersionName(): String {
        return try {
            val packageInfo = context?.packageManager?.getPackageInfo(context!!.packageName, 0)
            packageInfo?.versionName ?: "Unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    actual fun getVersionCode(): Int {
        return try {
            val packageInfo = context?.packageManager?.getPackageInfo(context!!.packageName, 0)
            packageInfo?.versionCode ?: 0
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }
}