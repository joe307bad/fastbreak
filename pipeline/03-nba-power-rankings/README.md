# NBA Elo Power Rankings

Build NBA power rankings using the Elo rating system with the hoopR R package.

## Phase 1: Load Game Data

```r
library(hoopR)
library(tidyverse)

# Load team box scores with rate limiting
team_box <- load_nba_team_box()
Sys.sleep(runif(1, 1, 3))  # Random delay between 1-3 seconds

# Process 2024 season first to build up ratings
games_2024 <- team_box %>%
  filter(season == 2024) %>%
  arrange(game_date)

# Then load 2025 season games (current season)
games_2025 <- team_box %>%
  filter(season == 2025) %>%
  arrange(game_date)

# Combine: process 2024 first, then carry forward to 2025
games_all <- bind_rows(games_2024, games_2025)
```

## Phase 2: Implement Elo Algorithm

```r
# Initialize all teams at 1500 Elo
INITIAL_ELO <- 1500
HOME_ADVANTAGE <- 100
K_FACTOR <- 20

# Calculate win probability
calc_win_prob <- function(elo_a, elo_b) {
  1 / (1 + 10^((elo_b - elo_a) / 400))
}

# Update Elo after each game
update_elo <- function(winner_elo, loser_elo, point_diff, k = K_FACTOR) {
  expected <- calc_win_prob(winner_elo, loser_elo)
  mov_mult <- log(abs(point_diff) + 1) * (2.2 / ((winner_elo - loser_elo) * 0.001 + 2.2))
  change <- k * mov_mult * (1 - expected)

  list(winner_new = winner_elo + change, loser_new = loser_elo - change)
}

# Process games chronologically
for (each game) {
  # Get current Elo ratings
  # Apply home court advantage (+100)
  # Update ratings based on outcome

  # Between seasons: apply regression to mean
  if (season changes from 2024 to 2025) {
    team_elo$elo_rating <- 1505 + 0.75 * (team_elo$elo_rating - 1505)
  }
}
```

## Phase 3: Generate Rankings & Validate

```r
# Current power rankings
rankings <- team_elo %>%
  arrange(desc(elo_rating)) %>%
  mutate(rank = row_number())

# Output to CSV
write_csv(rankings, "nba_elo_rankings.csv")
write_csv(game_results, "nba_elo_game_predictions.csv")

# Evaluate predictions
accuracy <- mean((home_win_prob > 0.5) == home_win_actual)
brier_score <- mean((home_win_prob - home_win_actual)^2)

cat("Prediction Accuracy:", round(accuracy * 100, 2), "%\n")
cat("Brier Score:", round(brier_score, 4), "\n")

# Visualize
ggplot(team_elo, aes(x = reorder(team, elo_rating), y = elo_rating)) +
  geom_col() +
  coord_flip()
```

## Resources

- [hoopR Documentation](https://hoopr.sportsdataverse.org/)
- [FiveThirtyEight Elo Methodology](https://fivethirtyeight.com/features/how-we-calculate-nba-elo-ratings/)
