package com.joebad.fastbreak.platform

/**
 * Platform-specific URL launcher
 */
expect object UrlLauncher {
    /**
     * Opens a URL in the default browser
     * @param url The URL to open
     */
    fun openUrl(url: String)
}
