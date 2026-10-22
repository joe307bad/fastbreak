package com.joebad.fastbreak.testing

import com.joebad.fastbreak.ui.theme.SelectedTeamTheme
import com.joebad.fastbreak.ui.theme.ThemeMode

/**
 * Overrides a UI test can set before the app composes anything.
 *
 * Screenshot runs need two things the shipping app has no reason to offer: a
 * different API host, so captures come from a local WireMock instead of the
 * production CDN, and a theme chosen per screenshot rather than read from
 * whatever the simulator last persisted.
 *
 * The platform entry point fills this in from its launch arguments. Nothing
 * reads it unless a launch argument set it, so a normal launch behaves exactly
 * as before.
 */
object UiTestOverrides {

    /** Replaces the CloudFront host when a test points the app at a mock. */
    var baseUrl: String? = null
        private set

    /** Forces light or dark instead of following the system. */
    var themeMode: ThemeMode? = null
        private set

    /** Forces a team palette, or clears it when the argument says "none". */
    var teamTheme: SelectedTeamTheme? = null
        private set

    /** True once a test asked for a team palette, including asking for none. */
    var teamThemeWasSet: Boolean = false
        private set

    val isUiTest: Boolean
        get() = baseUrl != null || themeMode != null || teamThemeWasSet

    /**
     * Reads the arguments a UI test launched the app with. Recognized forms,
     * each a flag followed by its value:
     *
     *   -fastbreakBaseUrl http://127.0.0.1:8089
     *   -fastbreakTheme light | dark
     *   -fastbreakTeamTheme nhl:PIT | none
     */
    fun applyLaunchArguments(arguments: List<String>) {
        arguments.forEachIndexed { index, argument ->
            val value = arguments.getOrNull(index + 1)?.takeIf { !it.startsWith("-") }
            when (argument) {
                "-fastbreakBaseUrl" -> value?.let { baseUrl = it.trimEnd('/') }
                "-fastbreakTheme" -> themeMode = when (value?.lowercase()) {
                    "light" -> ThemeMode.LIGHT
                    "dark" -> ThemeMode.DARK
                    else -> null
                }
                "-fastbreakTeamTheme" -> {
                    teamThemeWasSet = true
                    val parts = value?.split(":")
                    teamTheme = if (parts != null && parts.size == 2 && parts[0].lowercase() != "none") {
                        SelectedTeamTheme(sport = parts[0].lowercase(), teamCode = parts[1].uppercase())
                    } else {
                        null
                    }
                }
            }
        }

        if (isUiTest) {
            println("🧪 UI test overrides — baseUrl=${baseUrl ?: "(default)"}, " +
                "theme=${themeMode?.name ?: "(system)"}, " +
                "teamTheme=${teamTheme?.let { "${it.sport}:${it.teamCode}" } ?: "(none)"}")
        }
    }

    /** Test-only reset, so one process can exercise several argument sets. */
    fun reset() {
        baseUrl = null
        themeMode = null
        teamTheme = null
        teamThemeWasSet = false
    }
}
