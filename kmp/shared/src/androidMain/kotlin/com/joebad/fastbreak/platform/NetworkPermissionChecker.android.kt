package com.joebad.fastbreak.platform

import com.mohamedrejeb.calf.permissions.PermissionStatus

/**
 * Android implementation of NetworkPermissionChecker.
 * No special permission needed for local network access on Android.
 */
actual class NetworkPermissionChecker {
    actual suspend fun checkPermission(): PermissionStatus = PermissionStatus.Granted

    actual suspend fun requestPermission(): PermissionStatus = PermissionStatus.Granted
}
