package com.joebad.fastbreak.platform

/**
 * Provides app version information from the native platform.
 */
expect object AppVersion {
    /**
     * The version name (e.g., "1.0.0" or "0.0.1-alpha-42")
     */
    val versionName: String

    /**
     * The build/version code (e.g., "42")
     */
    val buildNumber: String
}
