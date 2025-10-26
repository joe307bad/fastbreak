#!/usr/bin/env Rscript
# Explore the NBA data to understand the structure

library(hoopR)
library(tidyverse)

cat("=== Exploring NBA Data ===\n\n")

# Load team box scores
team_box <- load_nba_team_box()

cat("Total rows:", nrow(team_box), "\n\n")

# Check what seasons are available
cat("Unique seasons in data:\n")
print(unique(team_box$season))

cat("\nSeason counts:\n")
print(table(team_box$season))

cat("\nSeason types:\n")
print(table(team_box$season_type))

cat("\nDate range:\n")
cat("  Min:", as.character(min(team_box$game_date, na.rm = TRUE)), "\n")
cat("  Max:", as.character(max(team_box$game_date, na.rm = TRUE)), "\n\n")

# Sample of data
cat("Sample data (first 5 rows):\n")
print(team_box %>%
  select(game_id, season, season_type, game_date, team_display_name, team_home_away, team_score, opponent_team_display_name) %>%
  head(5))

cat("\nLast 5 games:\n")
print(team_box %>%
  select(game_id, season, season_type, game_date, team_display_name, team_home_away, team_score, opponent_team_display_name) %>%
  tail(5))
