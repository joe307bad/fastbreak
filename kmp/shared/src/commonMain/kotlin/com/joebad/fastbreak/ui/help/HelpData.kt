package com.joebad.fastbreak.ui.help

data class HelpContent(
    val title: String,
    val description: String
)

enum class HelpPage {
    HOME,
    LEADERBOARD,
    PROFILE,
    SETTINGS,
    STAT_SHEET,
    DAILY_FASTBREAK_CARD,
    FASTBREAK_RESULTS_CARD
}

object HelpData {
    private val helpContent = mapOf(
        HelpPage.HOME to HelpContent(
            title = "Home",
            description = "This is the main screen where you can view daily challenges and make your picks. Select your predictions for upcoming games and track your progress."
        ),
        HelpPage.LEADERBOARD to HelpContent(
            title = "Leaderboard",
            description = "View rankings and compare your performance with other players. See who's leading the pack and track your position over time."
        ),
        HelpPage.PROFILE to HelpContent(
            title = "Profile",
            description = "Manage your account settings, view your stats, and customize your profile information. Update your username and track your overall performance."
        ),
        HelpPage.SETTINGS to HelpContent(
            title = "Settings",
            description = "Configure app preferences, sync data, and manage your account settings. Customize the app to your liking and manage data synchronization."
        ),
        HelpPage.STAT_SHEET to HelpContent(
            title = "My Stat Sheet",
            description = "Your personal statistics and performance metrics. View your recent activity, track your progress over time, and see detailed breakdowns of your predictions and results. Interactive buttons allow you to explore specific data points and trends."
        ),
        HelpPage.DAILY_FASTBREAK_CARD to HelpContent(
            title = "Daily Fastbreak Card",
            description = "Your daily prediction card for today's games. Make your selections for each game by choosing the winner, over/under totals, and other betting options. Once you're confident in your picks, lock your card to submit your predictions."
        ),
        HelpPage.FASTBREAK_RESULTS_CARD to HelpContent(
            title = "Fastbreak Results",
            description = "Review the results of your previous predictions. See which picks were correct, track your accuracy, and analyze your performance. Green checkmarks indicate correct predictions, while red X's show incorrect ones."
        )
    )
    
    fun getHelpContent(page: HelpPage): HelpContent {
        return helpContent[page] ?: HelpContent(
            title = "Help",
            description = "Help information for this page is not available."
        )
    }
}