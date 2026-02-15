package com.joebad.fastbreak.ui.bracket

/**
 * Represents a single team entry in a bracket matchup.
 */
data class BracketTeam(
    val seed: Int,
    val name: String,
    val score: Int? = null
)

/**
 * Represents a single matchup in the bracket.
 */
data class BracketMatchup(
    val team1: BracketTeam,
    val team2: BracketTeam,
    val winner: Int? = null // 1 or 2, null if not yet played
)

/**
 * Represents a full tournament bracket.
 */
data class BracketRound(
    val name: String,
    val matchups: List<BracketMatchup>
)

/**
 * Complete bracket data.
 */
data class BracketData(
    val title: String,
    val rounds: List<BracketRound>
)

/**
 * Fake bracket data for the prototype: an 8-team single-elimination bracket.
 */
fun createFakeBracketData(): BracketData {
    return BracketData(
        title = "2026 NCAA Tournament - East Region",
        rounds = listOf(
            BracketRound(
                name = "Round of 64",
                matchups = listOf(
                    BracketMatchup(
                        team1 = BracketTeam(1, "Duke", 82),
                        team2 = BracketTeam(16, "Norfolk St", 55),
                        winner = 1
                    ),
                    BracketMatchup(
                        team1 = BracketTeam(8, "Wisconsin", 64),
                        team2 = BracketTeam(9, "Memphis", 67),
                        winner = 2
                    ),
                    BracketMatchup(
                        team1 = BracketTeam(4, "Auburn", 78),
                        team2 = BracketTeam(13, "Vermont", 61),
                        winner = 1
                    ),
                    BracketMatchup(
                        team1 = BracketTeam(5, "Gonzaga", 70),
                        team2 = BracketTeam(12, "McNeese", 72),
                        winner = 2
                    )
                )
            ),
            BracketRound(
                name = "Round of 32",
                matchups = listOf(
                    BracketMatchup(
                        team1 = BracketTeam(1, "Duke", 75),
                        team2 = BracketTeam(9, "Memphis", 68),
                        winner = 1
                    ),
                    BracketMatchup(
                        team1 = BracketTeam(4, "Auburn", 80),
                        team2 = BracketTeam(12, "McNeese", 65),
                        winner = 1
                    )
                )
            ),
            BracketRound(
                name = "Sweet 16",
                matchups = listOf(
                    BracketMatchup(
                        team1 = BracketTeam(1, "Duke", 71),
                        team2 = BracketTeam(4, "Auburn", 69),
                        winner = 1
                    )
                )
            )
        )
    )
}
