package com.joebad.fastbreak.ui.help

data class HelpItem(
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
        HelpPage.HOME to listOf(
            HelpItem(
                title = "Home Screen",
                description = "This is the main screen where you can view daily challenges and make your picks."
            ),
            HelpItem(
                title = "Making Predictions",
                description = "Select your predictions for upcoming games and track your progress throughout the day."
            ),
            HelpItem(
                title = "Navigation",
                description = "Use the bottom navigation to access different sections of the app like Leaderboard and Profile."
            )
        ),
        HelpPage.LEADERBOARD to listOf(
            HelpItem(
                title = "Rankings",
                description = "View rankings and compare your performance with other players."
            ),
            HelpItem(
                title = "Weekly Progress",
                description = "See who's leading the pack and track your position over time."
            ),
            HelpItem(
                title = "Competition",
                description = "Challenge yourself to climb higher in the rankings and beat your friends."
            )
        ),
        HelpPage.PROFILE to listOf(
            HelpItem(
                title = "Account Settings",
                description = "Manage your account settings and customize your profile information."
            ),
            HelpItem(
                title = "Statistics",
                description = "View your personal stats and track your overall performance."
            ),
            HelpItem(
                title = "Username",
                description = "Update your username and profile details to personalize your experience."
            )
        ),
        HelpPage.SETTINGS to listOf(
            HelpItem(
                title = "App Preferences",
                description = "Configure app preferences and customize the app to your liking."
            ),
            HelpItem(
                title = "Data Sync",
                description = "Manage data synchronization to keep your predictions up to date."
            ),
            HelpItem(
                title = "Account Management",
                description = "Access account settings and manage your login preferences."
            )
        ),
        HelpPage.STAT_SHEET to listOf(
            HelpItem(
                title = "Personal Statistics",
                description = "Your personal statistics and performance metrics for tracking progress."
            ),
            HelpItem(
                title = "Recent Activity",
                description = "View your recent activity and track your progress over time."
            ),
            HelpItem(
                title = "Detailed Breakdowns",
                description = "See detailed breakdowns of your predictions and results."
            ),
            HelpItem(
                title = "Interactive Features",
                description = "Interactive buttons allow you to explore specific data points and trends."
            )
        ),
        HelpPage.DAILY_FASTBREAK_CARD to listOf(
            HelpItem(
                title = "Daily Predictions",
                description = "Your daily prediction card for today's games and upcoming matches."
            ),
            HelpItem(
                title = "Game Selections",
                description = "Make your selections for each game by choosing the winner and other betting options."
            ),
            HelpItem(
                title = "Over/Under Totals",
                description = "Predict whether the total score will be over or under the projected amount."
            ),
            HelpItem(
                title = "Locking Your Card",
                description = "Once you're confident in your picks, lock your card to submit your predictions."
            )
        ),
        HelpPage.FASTBREAK_RESULTS_CARD to listOf(
            HelpItem(
                title = "Results Review",
                description = "Review the results of your previous predictions and see how you performed."
            ),
            HelpItem(
                title = "Accuracy Tracking",
                description = "See which picks were correct and track your accuracy over time."
            ),
            HelpItem(
                title = "Performance Analysis",
                description = "Analyze your performance with detailed breakdowns of your predictions."
            ),
            HelpItem(
                title = "Visual Indicators",
                description = "Green checkmarks indicate correct predictions, while red X's show incorrect ones."
            )
        )
    )
    
    fun getHelpContent(page: HelpPage): List<HelpItem> {
        return helpContent[page] ?: listOf(
            HelpItem(
                title = "Help",
                description = "Help information for this page is not available."
            )
        )
    }
}