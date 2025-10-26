#!/usr/bin/env Rscript
# Add team statistics to final rankings CSV
# Adds W-L records, PPG, and points allowed

library(tidyverse)

cat("=== Adding Team Statistics to Rankings ===\n\n")

# Load data
cat("Loading data...\n")
rankings <- read_csv("data/nba_elo_rankings_final.csv", show_col_types = FALSE)
game_results <- read_csv("data/nba_elo_game_predictions.csv", show_col_types = FALSE)

cat("Loaded", nrow(rankings), "teams\n")
cat("Loaded", nrow(game_results), "game results\n\n")

# Calculate team statistics
cat("Calculating team statistics...\n")

# Create home team stats
home_stats <- game_results %>%
  group_by(team = home_team, season) %>%
  summarize(
    home_games = n(),
    home_wins = sum(home_win_actual),
    home_losses = sum(!home_win_actual),
    home_points_scored = sum(home_score),
    home_points_allowed = sum(away_score),
    .groups = "drop"
  )

# Create away team stats
away_stats <- game_results %>%
  group_by(team = away_team, season) %>%
  summarize(
    away_games = n(),
    away_wins = sum(!home_win_actual),
    away_losses = sum(home_win_actual),
    away_points_scored = sum(away_score),
    away_points_allowed = sum(home_score),
    .groups = "drop"
  )

# Combine home and away stats
team_stats <- full_join(home_stats, away_stats, by = c("team", "season")) %>%
  mutate(
    home_games = replace_na(home_games, 0),
    away_games = replace_na(away_games, 0),
    home_wins = replace_na(home_wins, 0),
    away_wins = replace_na(away_wins, 0),
    home_losses = replace_na(home_losses, 0),
    away_losses = replace_na(away_losses, 0),
    home_points_scored = replace_na(home_points_scored, 0),
    away_points_scored = replace_na(away_points_scored, 0),
    home_points_allowed = replace_na(home_points_allowed, 0),
    away_points_allowed = replace_na(away_points_allowed, 0)
  ) %>%
  mutate(
    total_games = home_games + away_games,
    wins = home_wins + away_wins,
    losses = home_losses + away_losses,
    points_scored = home_points_scored + away_points_scored,
    points_allowed = home_points_allowed + away_points_allowed,
    ppg = points_scored / total_games,
    pa_pg = points_allowed / total_games
  ) %>%
  select(team, season, total_games, wins, losses, ppg, pa_pg)

cat("✓ Team statistics calculated\n\n")

# Get 2024-25 season stats (season = 2025)
stats_2025 <- team_stats %>%
  filter(season == 2025) %>%
  select(team,
         wins_2024_25 = wins,
         losses_2024_25 = losses,
         games_2024_25 = total_games)

# Get 2025-26 season stats (season = 2026)
stats_2026 <- team_stats %>%
  filter(season == 2026) %>%
  select(team,
         wins_2025_26 = wins,
         losses_2025_26 = losses,
         ppg_2025_26 = ppg,
         pa_2025_26 = pa_pg,
         games_2025_26 = total_games)

# Join with rankings
cat("Merging statistics with rankings...\n")
enhanced_rankings <- rankings %>%
  left_join(stats_2026, by = "team") %>%
  left_join(stats_2025, by = "team") %>%
  mutate(
    # Fill NA values with 0 for teams that haven't played
    wins_2025_26 = replace_na(wins_2025_26, 0),
    losses_2025_26 = replace_na(losses_2025_26, 0),
    ppg_2025_26 = replace_na(ppg_2025_26, 0),
    pa_2025_26 = replace_na(pa_2025_26, 0),
    games_2025_26 = replace_na(games_2025_26, 0),
    wins_2024_25 = replace_na(wins_2024_25, 0),
    losses_2024_25 = replace_na(losses_2024_25, 0),
    games_2024_25 = replace_na(games_2024_25, 0),
    # Create W-L record strings
    record_2025_26 = paste0(wins_2025_26, "-", losses_2025_26),
    record_2024_25 = paste0(wins_2024_25, "-", losses_2024_25)
  ) %>%
  select(
    rank,
    team,
    elo_rating,
    record_2025_26,
    ppg_2025_26,
    pa_2025_26,
    record_2024_25,
    games_played
  ) %>%
  arrange(rank)

cat("✓ Statistics merged\n\n")

# Display preview
cat("=== Enhanced Rankings Preview ===\n")
cat("\nTop 10 Teams:\n")
print(head(enhanced_rankings, 10), n = 10)

cat("\nBottom 5 Teams:\n")
print(tail(enhanced_rankings, 5), n = 5)

# Save enhanced rankings
output_file <- "data/nba_elo_rankings_enhanced.csv"
write_csv(enhanced_rankings, output_file)

cat("\n✓ Enhanced rankings saved to:", output_file, "\n")

# Also create a nicely formatted version
formatted_rankings <- enhanced_rankings %>%
  mutate(
    elo_rating = round(elo_rating, 1),
    ppg_2025_26 = round(ppg_2025_26, 1),
    pa_2025_26 = round(pa_2025_26, 1)
  )

formatted_file <- "data/nba_elo_rankings_formatted.csv"
write_csv(formatted_rankings, formatted_file)

cat("✓ Formatted rankings saved to:", formatted_file, "\n")

# Print summary
cat("\n=== Summary Statistics ===\n")
cat("Teams with 2025-26 games played:", sum(enhanced_rankings$games_2025_26 > 0), "\n")
cat("Average PPG (2025-26):", round(mean(enhanced_rankings$ppg_2025_26[enhanced_rankings$ppg_2025_26 > 0]), 1), "\n")
cat("Average PA (2025-26):", round(mean(enhanced_rankings$pa_2025_26[enhanced_rankings$pa_2025_26 > 0]), 1), "\n")

cat("\n=== Complete ===\n")
