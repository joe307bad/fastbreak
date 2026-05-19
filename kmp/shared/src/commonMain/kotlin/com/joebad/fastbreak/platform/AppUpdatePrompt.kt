package com.joebad.fastbreak.platform

/**
 * Platform-specific app update prompt functionality.
 * Used to prompt users to update when the server requires a newer release ID.
 */
expect object AppUpdatePrompt {
    /**
     * Opens the appropriate app store for the platform.
     * - Android: Opens Google Play Store
     * - iOS: Opens Apple App Store
     */
    fun openAppStore()
}
