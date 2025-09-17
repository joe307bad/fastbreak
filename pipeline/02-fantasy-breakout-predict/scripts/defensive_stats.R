#!/usr/bin/env Rscript

# Load required libraries
suppressPackageStartupMessages({
  library(nflfastR)
  library(dplyr)
  library(tidyr)
  library(readr)
  library(gt)
  library(webshot2)
})

# Parse command line arguments
args <- commandArgs(trailingOnly = TRUE)

# Check if correct number of arguments provided
if (length(args) != 2) {
  cat("Usage: Rscript defensive_stats.R [year] [output_file]\n")
  cat("Example: Rscript defensive_stats.R 2023 defense_rankings_2023.csv\n")
  quit(status = 1)
}

year <- as.integer(args[1])
output_file <- args[2]

# Validate year
current_year <- as.integer(format(Sys.Date(), "%Y"))
if (year < 1999 || year > current_year) {
  cat("Error: Year must be between 1999 and", current_year, "\n")
  quit(status = 1)
}

cat("Fetching NFL play-by-play data for", year, "season...\n")

# Add random sleep between 1-3 seconds to avoid rate limiting
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds to avoid rate limiting...\n")
Sys.sleep(sleep_time)

# Load play-by-play data for the specified season
tryCatch({
  pbp_data <- load_pbp(year)
}, error = function(e) {
  cat("Error loading play-by-play data:", e$message, "\n")
  quit(status = 1)
})

cat("Processing defensive statistics...\n")

# Filter for regular season games only
regular_season <- pbp_data %>%
  filter(season_type == "REG")

# Calculate defensive stats for each team
defensive_stats <- regular_season %>%
  filter(!is.na(posteam) & !is.na(defteam)) %>%
  group_by(defteam, game_id) %>%
  summarise(
    # Calculate points allowed correctly by summing each scoring type separately
    points_allowed = sum(case_when(
      posteam == defteam ~ 0,
      touchdown == 1 ~ 6,
      field_goal_result == "made" ~ 3,
      safety == 1 ~ 2,
      extra_point_result == "good" ~ 1,
      two_point_conv_result == "success" ~ 2,
      TRUE ~ 0
    ), na.rm = TRUE),
    rushing_yards_allowed = sum(ifelse(play_type == "run" & posteam != defteam, yards_gained, 0), na.rm = TRUE),
    passing_yards_allowed = sum(ifelse(play_type == "pass" & posteam != defteam, yards_gained, 0), na.rm = TRUE),
    turnovers_forced = sum(ifelse(posteam != defteam, interception + fumble_lost, 0), na.rm = TRUE),
    .groups = "drop"
  ) %>%
  group_by(defteam) %>%
  summarise(
    games_played = n_distinct(game_id),
    total_points_allowed = sum(points_allowed),
    total_rushing_yards_allowed = sum(rushing_yards_allowed),
    total_passing_yards_allowed = sum(passing_yards_allowed),
    total_turnovers_forced = sum(turnovers_forced),
    points_allowed_per_game = round(total_points_allowed / games_played, 2),
    rushing_yards_allowed_per_game = round(total_rushing_yards_allowed / games_played, 2),
    passing_yards_allowed_per_game = round(total_passing_yards_allowed / games_played, 2),
    turnovers_per_game = round(total_turnovers_forced / games_played, 2),
    total_yards_allowed_per_game = round((total_rushing_yards_allowed + total_passing_yards_allowed) / games_played, 2),
    .groups = "drop"
  ) %>%
  arrange(points_allowed_per_game, total_yards_allowed_per_game)

# Add power ranking based on points allowed per game
# Also add separate rankings for rush and pass defense
defensive_stats <- defensive_stats %>%
  mutate(
    defense_rank = row_number(),
    # Add rankings for rush and pass defense (1 = best, 32 = worst)
    rush_defense_rank = rank(rushing_yards_allowed_per_game, ties.method = "min"),
    pass_defense_rank = rank(passing_yards_allowed_per_game, ties.method = "min"),
    season = year
  ) %>%
  select(
    defense_rank,
    team = defteam,
    season,
    games_played,
    points_allowed_per_game,
    rush_defense_rank,
    rushing_yards_allowed_per_game,
    pass_defense_rank,
    passing_yards_allowed_per_game,
    turnovers_per_game,
    total_yards_allowed_per_game
  )

# Add another random sleep before writing file
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds before saving results...\n")
Sys.sleep(sleep_time)

# Save to CSV
write_csv(defensive_stats, output_file)

# Create PNG table visualization
cat("\nGenerating table visualization...\n")

# Generate PNG filename from CSV filename
png_file <- sub("\\.csv$", ".png", output_file)
if (png_file == output_file) {
  png_file <- paste0(output_file, ".png")
}

# Create a formatted table with gt
table_viz <- defensive_stats %>%
  gt() %>%
  tab_header(
    title = paste("NFL Defense Power Rankings -", year, "Season"),
    subtitle = "Teams ranked by points allowed per game"
  ) %>%
  cols_label(
    defense_rank = "Rank",
    team = "Team",
    season = "Season",
    games_played = "Games",
    points_allowed_per_game = "Pts/Game",
    rush_defense_rank = "Rush Rank",
    rushing_yards_allowed_per_game = "Rush Yds/Game",
    pass_defense_rank = "Pass Rank",
    passing_yards_allowed_per_game = "Pass Yds/Game",
    turnovers_per_game = "TO/Game",
    total_yards_allowed_per_game = "Total Yds/Game"
  ) %>%
  fmt_number(
    columns = c(points_allowed_per_game, rushing_yards_allowed_per_game, 
                passing_yards_allowed_per_game, turnovers_per_game, 
                total_yards_allowed_per_game),
    decimals = 1
  ) %>%
  # First apply row highlighting for top 5 and bottom 5
  tab_style(
    style = list(
      cell_fill(color = "#E8F5E9"),
      cell_text(weight = "bold", color = "black")
    ),
    locations = cells_body(
      columns = c(defense_rank, team, season, games_played,
                  rushing_yards_allowed_per_game, passing_yards_allowed_per_game,
                  turnovers_per_game, total_yards_allowed_per_game),
      rows = defense_rank <= 5
    )
  ) %>%
  tab_style(
    style = list(
      cell_fill(color = "#FFEBEE"),
      cell_text(weight = "bold", color = "black")
    ),
    locations = cells_body(
      columns = c(defense_rank, team, season, games_played,
                  rushing_yards_allowed_per_game, passing_yards_allowed_per_game,
                  turnovers_per_game, total_yards_allowed_per_game),
      rows = defense_rank >= 28
    )
  ) %>%
  # Then apply gradient colors that override the row highlighting
  data_color(
    columns = points_allowed_per_game,
    colors = scales::col_numeric(
      palette = c("#006400", "#228B22", "#90EE90", "#FFFF99", "#FFB347", "#FF6B6B", "#8B0000"),
      domain = c(min(defensive_stats$points_allowed_per_game),
                 max(defensive_stats$points_allowed_per_game))
    )
  ) %>%
  data_color(
    columns = rush_defense_rank,
    colors = scales::col_numeric(
      palette = c("#004225", "#006400", "#228B22", "#90EE90", "#FFFACD", "#FFE4B5", "#FFA07A", "#FF6347", "#DC143C"),
      domain = c(1, 32)
    )
  ) %>%
  data_color(
    columns = pass_defense_rank,
    colors = scales::col_numeric(
      palette = c("#004225", "#006400", "#228B22", "#90EE90", "#FFFACD", "#FFE4B5", "#FFA07A", "#FF6347", "#DC143C"),
      domain = c(1, 32)
    )
  ) %>%
  tab_options(
    table.font.size = 12,
    heading.title.font.size = 16,
    heading.subtitle.font.size = 14,
    table.width = pct(100)
  ) %>%
  cols_align(
    align = "center",
    columns = everything()
  ) %>%
  cols_align(
    align = "left",
    columns = team
  ) %>%
  cols_align(
    align = "center",
    columns = c(rush_defense_rank, pass_defense_rank)
  )

# Save as PNG with rate limiting
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds before saving PNG...\n")
Sys.sleep(sleep_time)

# Save the table as PNG
gtsave(table_viz, png_file, expand = 10)

# Print summary
cat("\n=== Defense Power Rankings for", year, "Season ===\n")
cat("Top 5 Defenses (by points allowed per game):\n")
print(head(defensive_stats %>% select(defense_rank, team, points_allowed_per_game, total_yards_allowed_per_game), 5))

cat("\nBottom 5 Defenses (by points allowed per game):\n")
print(tail(defensive_stats %>% select(defense_rank, team, points_allowed_per_game, total_yards_allowed_per_game), 5))

cat("\nResults saved to:\n")
cat("  CSV:", output_file, "\n")
cat("  PNG:", png_file, "\n")
cat("Total teams analyzed:", nrow(defensive_stats), "\n")