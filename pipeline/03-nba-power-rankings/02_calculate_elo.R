#!/usr/bin/env Rscript
# Phase 2: Calculate Elo Ratings
# Implement Elo algorithm and process all games chronologically

library(tidyverse)

cat("=== Phase 2: Calculating Elo Ratings ===\n\n")

# Load game data from Phase 1
cat("Loading game data...\n")
games <- read_csv("data/games_2024_2025.csv", show_col_types = FALSE)
cat("Loaded", nrow(games), "games\n\n")

# Elo parameters
INITIAL_ELO <- 1500
HOME_ADVANTAGE <- 100
K_FACTOR <- 20
SEASON_REGRESSION <- 0.75  # Keep 75% of rating, regress 25% to mean

cat("Elo Parameters:\n")
cat("  Initial Elo:", INITIAL_ELO, "\n")
cat("  Home Court Advantage:", HOME_ADVANTAGE, "\n")
cat("  K-Factor:", K_FACTOR, "\n")
cat("  Season Regression:", SEASON_REGRESSION, "\n\n")

# Calculate win probability
calc_win_prob <- function(elo_a, elo_b) {
  1 / (1 + 10^((elo_b - elo_a) / 400))
}

# Calculate margin of victory multiplier (FiveThirtyEight formula)
calc_mov_multiplier <- function(point_diff, elo_winner, elo_loser) {
  log(abs(point_diff) + 1) * (2.2 / ((elo_winner - elo_loser) * 0.001 + 2.2))
}

# Update Elo ratings after a game
update_elo <- function(winner_elo, loser_elo, point_diff, k = K_FACTOR) {
  # Calculate expected win probability for winner
  expected_win <- calc_win_prob(winner_elo, loser_elo)

  # Calculate MOV multiplier
  mov_mult <- calc_mov_multiplier(point_diff, winner_elo, loser_elo)

  # Calculate rating change
  elo_change <- k * mov_mult * (1 - expected_win)

  # Return new ratings
  list(
    winner_new = winner_elo + elo_change,
    loser_new = loser_elo - elo_change,
    elo_change = elo_change,
    expected_win_prob = expected_win
  )
}

# Initialize team ratings
cat("Initializing team ratings...\n")
all_teams <- unique(c(games$home_team, games$away_team))
cat("Found", length(all_teams), "unique teams\n\n")

team_elo <- tibble(
  team = all_teams,
  elo_rating = INITIAL_ELO,
  games_played = 0
)

# Filter out All-Star teams (optional)
all_star_teams <- c("Team Candace", "Team Chuck", "Team Kenny", "Team Shaq")
games_filtered <- games %>%
  filter(!home_team %in% all_star_teams & !away_team %in% all_star_teams)

cat("Filtered games (excluding All-Star):", nrow(games_filtered), "\n")
cat("Processing games chronologically...\n\n")

# Initialize results storage
game_results <- tibble()

# Track when season changes for regression
current_season <- NA

# Process each game chronologically
for (i in 1:nrow(games_filtered)) {
  game <- games_filtered[i, ]

  # Check if season changed (apply regression to mean)
  if (!is.na(current_season) && game$season != current_season) {
    cat("Season transition detected:", current_season, "->", game$season, "\n")
    cat("Applying regression to mean (", SEASON_REGRESSION * 100, "%)\n", sep = "")

    # Apply regression: new_elo = 1505 + regression * (old_elo - 1505)
    team_elo <- team_elo %>%
      mutate(elo_rating = 1505 + SEASON_REGRESSION * (elo_rating - 1505))

    cat("Regression complete\n\n")
  }
  current_season <- game$season

  # Get current ratings
  home_elo <- team_elo$elo_rating[team_elo$team == game$home_team]
  away_elo <- team_elo$elo_rating[team_elo$team == game$away_team]

  # Apply home court advantage (add to home team's Elo for calculation only)
  home_elo_adj <- home_elo + HOME_ADVANTAGE

  # Calculate win probability (for home team)
  home_win_prob <- calc_win_prob(home_elo_adj, away_elo)

  # Determine winner and loser
  if (game$home_win) {
    # Home team won
    update <- update_elo(home_elo, away_elo, game$point_diff, K_FACTOR)

    # Update ratings
    team_elo$elo_rating[team_elo$team == game$home_team] <- update$winner_new
    team_elo$elo_rating[team_elo$team == game$away_team] <- update$loser_new

    elo_change <- update$elo_change
  } else {
    # Away team won
    update <- update_elo(away_elo, home_elo, game$point_diff, K_FACTOR)

    # Update ratings
    team_elo$elo_rating[team_elo$team == game$away_team] <- update$winner_new
    team_elo$elo_rating[team_elo$team == game$home_team] <- update$loser_new

    elo_change <- update$elo_change
  }

  # Store game result with predictions
  game_results <- bind_rows(game_results, tibble(
    game_id = game$game_id,
    game_date = game$game_date,
    season = game$season,
    home_team = game$home_team,
    away_team = game$away_team,
    home_score = game$home_score,
    away_score = game$away_score,
    home_elo_pre = home_elo,
    away_elo_pre = away_elo,
    home_elo_post = team_elo$elo_rating[team_elo$team == game$home_team],
    away_elo_post = team_elo$elo_rating[team_elo$team == game$away_team],
    home_win_prob = home_win_prob,
    home_win_actual = game$home_win,
    elo_change = elo_change,
    point_diff = game$point_diff
  ))

  # Increment games played
  team_elo$games_played[team_elo$team == game$home_team] <-
    team_elo$games_played[team_elo$team == game$home_team] + 1
  team_elo$games_played[team_elo$team == game$away_team] <-
    team_elo$games_played[team_elo$team == game$away_team] + 1

  # Progress update every 100 games
  if (i %% 100 == 0) {
    cat("Processed", i, "/", nrow(games_filtered), "games\n")
  }
}

cat("\n✓ All games processed\n\n")

# Calculate evaluation metrics
cat("=== Evaluation Metrics ===\n")

# Prediction accuracy (did we predict the right winner?)
accuracy <- mean((game_results$home_win_prob > 0.5) == game_results$home_win_actual)
cat("Prediction Accuracy:", round(accuracy * 100, 2), "%\n")

# Brier Score (measures probability calibration, lower is better)
brier_score <- mean((game_results$home_win_prob - as.numeric(game_results$home_win_actual))^2)
cat("Brier Score:", round(brier_score, 4), "\n")

# Log Loss (lower is better)
log_loss <- -mean(
  as.numeric(game_results$home_win_actual) * log(pmax(game_results$home_win_prob, 1e-15)) +
  (1 - as.numeric(game_results$home_win_actual)) * log(pmax(1 - game_results$home_win_prob, 1e-15))
)
cat("Log Loss:", round(log_loss, 4), "\n\n")

# Calculate metrics by season
cat("=== Metrics by Season ===\n")
season_metrics <- game_results %>%
  group_by(season) %>%
  summarize(
    games = n(),
    accuracy = mean((home_win_prob > 0.5) == home_win_actual),
    brier_score = mean((home_win_prob - as.numeric(home_win_actual))^2),
    .groups = "drop"
  )

print(season_metrics)

cat("\n=== Current Elo Ratings ===\n")
rankings <- team_elo %>%
  arrange(desc(elo_rating)) %>%
  mutate(rank = row_number())

print(rankings, n = 30)

# Save outputs
cat("\n=== Saving Results ===\n")

# Current rankings
write_csv(rankings, "data/nba_elo_rankings.csv")
cat("✓ Rankings saved to: data/nba_elo_rankings.csv\n")

# Game predictions
write_csv(game_results, "data/nba_elo_game_predictions.csv")
cat("✓ Game predictions saved to: data/nba_elo_game_predictions.csv\n")

# Summary report
summary_file <- "data/elo_summary.txt"
sink(summary_file)
cat("=== NBA Elo Ratings Summary ===\n\n")
cat("Date calculated:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n\n")
cat("Parameters:\n")
cat("  Initial Elo:", INITIAL_ELO, "\n")
cat("  Home Court Advantage:", HOME_ADVANTAGE, "\n")
cat("  K-Factor:", K_FACTOR, "\n")
cat("  Season Regression:", SEASON_REGRESSION, "\n\n")
cat("Games Processed:", nrow(game_results), "\n")
cat("  2024-25 season:", sum(game_results$season == 2025), "\n")
cat("  2025-26 season:", sum(game_results$season == 2026), "\n\n")
cat("Overall Metrics:\n")
cat("  Prediction Accuracy:", round(accuracy * 100, 2), "%\n")
cat("  Brier Score:", round(brier_score, 4), "\n")
cat("  Log Loss:", round(log_loss, 4), "\n\n")
cat("Top 10 Teams (Current Elo):\n")
print(head(rankings, 10))
sink()

cat("✓ Summary saved to:", summary_file, "\n")

cat("\n=== Phase 2 Complete ===\n")
