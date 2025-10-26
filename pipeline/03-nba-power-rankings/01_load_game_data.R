#!/usr/bin/env Rscript
# Phase 1: Load NBA Game Data
# Load team box scores from hoopR and transform to game-level dataset

library(hoopR)
library(tidyverse)

cat("=== Phase 1: Loading NBA Game Data ===\n\n")

# NBA seasons use the ending year
# Season 2025 = 2024-25 season (full season to build ratings)
# Season 2026 = 2025-26 season (current season, just started)

# Load 2024-25 season with rate limiting
cat("Loading 2024-25 season (season = 2025)...\n")
games_2025 <- load_nba_team_box(seasons = 2025)
Sys.sleep(runif(1, 1, 3))  # Random delay between 1-3 seconds

cat("2024-25 season rows:", nrow(games_2025), "\n")

# Load 2025-26 season (current season)
cat("Loading 2025-26 season (season = 2026)...\n")
games_2026 <- load_nba_team_box(seasons = 2026)
Sys.sleep(runif(1, 1, 3))  # Random delay between 1-3 seconds

cat("2025-26 season rows:", nrow(games_2026), "\n\n")

# Combine: process 2025 first, then carry forward to 2026
games_all <- bind_rows(games_2025, games_2026)

cat("Total combined rows:", nrow(games_all), "\n\n")

# Transform from team-level to game-level rows
# Currently we have 2 rows per game (one for each team)
# We need 1 row per game with home/away structure
cat("Transforming to game-level dataset...\n")

# Inspect the data structure
cat("\nColumn names in games_all:\n")
print(names(games_all))

cat("\nFirst few rows:\n")
print(head(games_all, 10))

# Identify home/away and create game-level dataset
# This will depend on the actual structure of the data
# Common approaches:
# 1. Filter for home games only
# 2. Group by game_id and pivot wider
# 3. Use team_home_away indicator if available

# Let's examine a sample game to understand the structure
cat("\nSample game (first game_id):\n")
sample_game_id <- games_all$game_id[1]
sample_game <- games_all %>% filter(game_id == sample_game_id)
print(sample_game)

# Create game-level dataset
# Strategy: Keep only one row per game (home team row) and add away team info
games_clean <- games_all %>%
  # Sort to ensure consistent ordering
  arrange(game_id, desc(team_home_away)) %>%
  # Group by game to get both teams
  group_by(game_id) %>%
  filter(n() == 2) %>%  # Only keep games with exactly 2 teams
  summarize(
    game_date = first(game_date),
    season = first(season),
    # Assuming team_home_away exists or we can determine from other fields
    # We'll extract home and away team info
    home_team = ifelse(first(team_home_away) == "home", first(team_display_name), last(team_display_name)),
    away_team = ifelse(first(team_home_away) == "away", first(team_display_name), last(team_display_name)),
    home_score = ifelse(first(team_home_away) == "home", first(team_score), last(team_score)),
    away_score = ifelse(first(team_home_away) == "away", first(team_score), last(team_score)),
    .groups = "drop"
  ) %>%
  mutate(
    home_win = home_score > away_score,
    point_diff = abs(home_score - away_score)
  ) %>%
  arrange(game_date)

cat("\nGame-level dataset created.\n")
cat("Total games:", nrow(games_clean), "\n")
cat("2024-25 season games:", sum(games_clean$season == 2025), "\n")
cat("2025-26 season games:", sum(games_clean$season == 2026), "\n\n")

# Display summary
cat("Summary of game data:\n")
print(summary(games_clean))

cat("\nFirst 10 games:\n")
print(head(games_clean, 10))

cat("\nLast 10 games (most recent):\n")
print(tail(games_clean, 10))

# Save to CSV
output_file <- "data/games_2024_2025.csv"

# Create data directory if it doesn't exist
if (!dir.exists("data")) {
  dir.create("data")
  cat("\nCreated 'data' directory.\n")
}

write_csv(games_clean, output_file)
cat("\n✓ Game data saved to:", output_file, "\n")

# Save summary stats
summary_file <- "data/data_summary.txt"
sink(summary_file)
cat("=== NBA Game Data Summary ===\n\n")
cat("Date processed:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n\n")
cat("Total games:", nrow(games_clean), "\n")
cat("2024-25 season games:", sum(games_clean$season == 2025), "\n")
cat("2025-26 season games:", sum(games_clean$season == 2026), "\n\n")
cat("Date range:\n")
cat("  First game:", as.character(min(games_clean$game_date)), "\n")
cat("  Last game:", as.character(max(games_clean$game_date)), "\n\n")
cat("Teams in dataset:\n")
all_teams <- unique(c(games_clean$home_team, games_clean$away_team))
cat(paste(sort(all_teams), collapse = "\n"), "\n")
sink()

cat("✓ Summary saved to:", summary_file, "\n")

cat("\n=== Phase 1 Complete ===\n")
