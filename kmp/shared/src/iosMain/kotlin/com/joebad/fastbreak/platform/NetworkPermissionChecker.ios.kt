package com.joebad.fastbreak.platform

import com.mohamedrejeb.calf.permissions.PermissionStatus

/**
 * iOS implementation of NetworkPermissionChecker.
 * Always returns Granted - iOS will handle local network permission prompts automatically
 * when the app makes its first network request.
 */
actual class NetworkPermissionChecker {
    /**
     * Always returns Granted.
     * iOS will prompt for local network access when needed.
     */
    actual suspend fun checkPermission(): PermissionStatus {
        return PermissionStatus.Granted
    }

    /**
     * Always returns Granted.
     * iOS will prompt for local network access when needed.
     */
    actual suspend fun requestPermission(): PermissionStatus {
        return PermissionStatus.Granted
    }
}
