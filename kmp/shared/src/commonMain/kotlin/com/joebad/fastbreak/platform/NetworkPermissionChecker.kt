package com.joebad.fastbreak.platform

import com.mohamedrejeb.calf.permissions.PermissionStatus

/**
 * Platform-specific network permission checker.
 * On iOS, checks for local network permission using Calf library.
 * On Android, always returns granted (no special permission needed for local network).
 */
expect class NetworkPermissionChecker() {
    /**
     * Checks the current network permission status.
     * On iOS: checks local network permission status via Calf.
     * On Android: always returns Granted.
     */
    suspend fun checkPermission(): PermissionStatus

    /**
     * Requests network permission.
     * On iOS: triggers the local network permission dialog via Calf.
     * On Android: does nothing (not needed).
     */
    suspend fun requestPermission(): PermissionStatus
}
