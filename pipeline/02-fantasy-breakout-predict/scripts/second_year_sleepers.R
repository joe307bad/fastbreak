#!/usr/bin/env Rscript

# Load required libraries
suppressPackageStartupMessages({
  library(nflfastR)
  library(dplyr)
  library(tidyr)
  library(readr)
  library(gt)
  library(webshot2)
  library(jsonlite)
  library(httr)
  library(nflreadr)
  library(ffpros)
})

# Parse command line arguments
args <- commandArgs(trailingOnly = TRUE)

# Check if correct number of arguments provided
if (length(args) != 2) {
  cat("Usage: Rscript second_year_sleepers.R [year] [output_file]\n")
  cat("Example: Rscript second_year_sleepers.R 2024 second_year_sleepers_2024.csv\n")
  quit(status = 1)
}

year <- as.integer(args[1])
output_file <- args[2]

# Validate year
current_year <- as.integer(format(Sys.Date(), "%Y"))
if (year < 2020 || year > current_year) {
  cat("Error: Year must be between 2020 and", current_year, "\n")
  quit(status = 1)
}

cat("Loading NFL roster data for", year, "season...\n")
cat("[DEBUG] Starting roster data fetch at:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n")

# Add random sleep between 1-3 seconds to avoid rate limiting
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds to avoid rate limiting...\n")
Sys.sleep(sleep_time)

# Load roster data
rosters <- fast_scraper_roster(year)
cat("[DEBUG] Loaded", nrow(rosters), "roster entries\n")

# Load ADP data from FantasyPros
cat("Fetching ADP data from FantasyPros...\n")
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds...\n")
Sys.sleep(sleep_time)

# Get actual ECR data from FantasyPros
cat("[DEBUG] Fetching ECR data from FantasyPros at:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n")
ecr_data <- tryCatch({
  # Get all rankings and filter for ECR > 100, then take first 200+ players
  # EXCLUDING QUARTERBACKS as per TODO #1
  all_rankings <- fp_rankings(page = 'consensus-cheatsheets', year = year, scoring = 'HALF') %>%
    filter(
      ecr > 100,  # Start from players ranked worse than 100
      pos %in% c("RB", "WR", "TE")  # Only RB, WR, TE positions (QBs excluded)
    ) %>%
    arrange(ecr) %>%  # Sort by ECR (best to worst)
    head(200) %>%     # Take first 200 players
    select(player = player_name, position = pos, team, ecr) %>%
    mutate(
      # Clean up position names to match nflfastR (QBs excluded)
      position = case_when(
        position == "RB" ~ "RB",
        position == "WR" ~ "WR", 
        position == "TE" ~ "TE",
        TRUE ~ position
      )
    )
  
  cat("Fetched", nrow(all_rankings), "players with ECR > 100 (excluding QBs)\n")
  cat("[DEBUG] Position breakdown:", table(all_rankings$position), "\n")
  cat("ECR range:", min(all_rankings$ecr), "to", max(all_rankings$ecr), "\n")
  
  all_rankings
}, error = function(e) {
  cat("Error: Could not fetch FantasyPros data:", e$message, "\n")
  cat("This script requires FantasyPros data to function.\n")
  quit(status = 1)
})

# Load previous year stats for context
cat("Loading previous season stats...\n")
cat("[DEBUG] Fetching stats for year", year - 1, "at:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n")
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds...\n")
Sys.sleep(sleep_time)

# Get previous year stats
prev_year_stats <- load_player_stats(year - 1) %>%
  filter(season_type == "REG") %>%
  group_by(player_id, player_display_name, position) %>%
  summarise(
    games = n_distinct(week),
    total_fantasy_points = sum(fantasy_points, na.rm = TRUE),
    ppg = round(total_fantasy_points / games, 2),
    .groups = "drop"
  ) %>%
  rename(player_name = player_display_name)

cat("[DEBUG] Loaded", nrow(prev_year_stats), "players' previous year stats\n")

# Clean names for matching function
clean_name <- function(name) {
  gsub("[^A-Za-z0-9 ]", "", tolower(trimws(name)))
}

# Load snap count data for previous year (Year 1)
cat("Loading previous season (Year 1) snap count data...\n")
cat("[DEBUG] Fetching snap counts for year", year - 1, "at:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n")
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds...\n")
Sys.sleep(sleep_time)

prev_year_snaps <- load_snap_counts(year - 1) %>%
  filter(game_type == "REG") %>%
  group_by(pfr_player_id, player, position, team) %>%
  summarise(
    total_games = n_distinct(week),
    total_off_snaps = sum(offense_snaps, na.rm = TRUE),
    avg_snap_pct_y1 = round(mean(offense_pct, na.rm = TRUE), 1),
    .groups = "drop"
  ) %>%
  # Clean player names for matching
  mutate(clean_snap_name = clean_name(player))

cat("[DEBUG] Loaded", nrow(prev_year_snaps), "players' Year 1 snap counts\n")

# Load snap count data for current year (Year 2)
cat("Loading current season (Year 2) snap count data...\n")
cat("[DEBUG] Fetching snap counts for year", year, "at:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n")
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds...\n")
Sys.sleep(sleep_time)

# First get weekly snap data for Year 2 to calculate week-to-week changes
year2_weekly_snaps <- load_snap_counts(year) %>%
  filter(game_type == "REG") %>%
  arrange(pfr_player_id, week) %>%
  group_by(pfr_player_id, player, position, team) %>%
  mutate(
    # Calculate the change in snap share from week to week
    snap_pct_lag = lag(offense_pct),
    weekly_snap_change = offense_pct - snap_pct_lag
  ) %>%
  ungroup()

# Calculate aggregated stats including average week-to-week change
current_year_snaps <- year2_weekly_snaps %>%
  group_by(pfr_player_id, player, position, team) %>%
  summarise(
    total_games_y2 = n_distinct(week),
    total_off_snaps_y2 = sum(offense_snaps, na.rm = TRUE),
    avg_snap_pct_y2 = round(mean(offense_pct, na.rm = TRUE), 1),
    # Get week 1 snap share for baseline
    w1_snap_share = first(offense_pct[week == min(week)], default = NA),
    # Calculate average snap share change between each week of Y2
    y2_snap_share_change = round(mean(weekly_snap_change, na.rm = TRUE), 2),
    .groups = "drop"
  ) %>%
  # Clean player names for matching
  mutate(clean_snap_name = clean_name(player))

cat("[DEBUG] Loaded", nrow(current_year_snaps), "players' Year 2 snap counts\n")

# First, match ECR data with rosters to identify second-year players
cat("Matching FantasyPros ECR data with second-year players...\n")

# Get second-year rosters with cleaned names (EXCLUDING QBs)
cat("[DEBUG] Filtering second-year players (excluding QBs)...\n")
second_year_rosters <- rosters %>%
  filter(
    position %in% c("RB", "WR", "TE"),  # Exclude QBs as per TODO #1
    entry_year == (year - 1)  # Players who entered the league last year
  ) %>%
  mutate(
    # Calculate age from birth_date if available
    age = if_else(!is.na(birth_date), 
                  as.integer(difftime(Sys.Date(), as.Date(birth_date), units = "days") / 365.25),
                  as.integer(22 + years_exp)),  # Estimate if birth_date not available
    clean_name = clean_name(full_name)
  ) %>%
  select(
    player_id = gsis_id,
    player = full_name,
    clean_name,
    position,
    team,
    entry_year,
    years_exp,
    age,
    draft_number,
    college,
    height,
    weight
  )

cat("[DEBUG] Found", nrow(second_year_rosters), "second-year players (RB/WR/TE only)\n")
cat("[DEBUG] Position breakdown:", table(second_year_rosters$position), "\n")

# Match ECR data with second-year players
ecr_with_clean_names <- ecr_data %>%
  mutate(clean_name = clean_name(player))

# Find second-year players who have ECR data between 100-200
cat("[DEBUG] Matching ECR data with second-year players...\n")
sleeper_candidates <- second_year_rosters %>%
  inner_join(
    ecr_with_clean_names %>% select(clean_name, position, player_ecr = player, ecr),
    by = c("clean_name", "position")
  )

cat("Found", nrow(sleeper_candidates), "second-year players with ECR > 100 (RB/WR/TE only)\n")
cat("[DEBUG] Final position breakdown:", table(sleeper_candidates$position), "\n")

# Join with previous year stats
sleeper_candidates <- sleeper_candidates %>%
  left_join(
    prev_year_stats %>% select(player_id, games, total_fantasy_points, ppg),
    by = "player_id"
  ) %>%
  mutate(
    games = coalesce(games, 0),
    total_fantasy_points = coalesce(total_fantasy_points, 0),
    ppg = coalesce(ppg, 0)
  )

# Join with previous year (Year 1) snap count data
sleeper_candidates <- sleeper_candidates %>%
  left_join(
    prev_year_snaps %>% select(clean_snap_name, position, avg_snap_pct_y1, total_off_snaps),
    by = c("clean_name" = "clean_snap_name", "position")
  ) %>%
  mutate(
    avg_snap_pct_y1 = coalesce(avg_snap_pct_y1, 0),
    total_off_snaps = coalesce(total_off_snaps, 0)
  )

# Join with current year (Year 2) snap count data
sleeper_candidates <- sleeper_candidates %>%
  left_join(
    current_year_snaps %>% select(clean_snap_name, position, avg_snap_pct_y2, total_games_y2, w1_snap_share, y2_snap_share_change),
    by = c("clean_name" = "clean_snap_name", "position")
  ) %>%
  mutate(
    avg_snap_pct_y2 = coalesce(avg_snap_pct_y2, 0),
    total_games_y2 = coalesce(total_games_y2, 0),
    w1_snap_share = coalesce(w1_snap_share, 0),
    y2_snap_share_change = coalesce(y2_snap_share_change, 0),
    # Calculate snap percentage change from Y1 to Y2
    snap_pct_change = avg_snap_pct_y2 - avg_snap_pct_y1
  ) %>%
  select(-clean_name)  # Remove helper column after all joins are complete

# Create a sleeper score based on various factors
# Higher score = better sleeper candidate
sleepers <- sleeper_candidates %>%
  mutate(
    # Calculate sleeper score
    draft_value = case_when(
      is.na(draft_number) ~ 50,  # UDFA bonus
      draft_number > 200 ~ 40,    # Late round bonus
      draft_number > 150 ~ 30,
      draft_number > 100 ~ 20,
      draft_number > 50 ~ 10,
      TRUE ~ 0
    ),
    
    # Position-based thresholds for sleeper status (QBs excluded)
    ppg_threshold = case_when(
      position == "RB" ~ 6,
      position == "WR" ~ 6,
      position == "TE" ~ 4,
      TRUE ~ 5
    ),
    
    # Performance score (low but promising)
    performance_score = case_when(
      ppg < ppg_threshold & ppg > 0 ~ 30,  # Low production but played
      ppg == 0 & games > 0 ~ 20,           # Played but minimal fantasy points
      ppg == 0 & games == 0 ~ 40,          # Didn't play (redshirt/injury)
      ppg >= ppg_threshold & ppg < ppg_threshold * 1.5 ~ 20,  # Moderate production
      TRUE ~ 0
    ),
    
    # Age bonus (younger is better)
    age_score = case_when(
      age <= 22 ~ 20,
      age <= 23 ~ 15,
      age <= 24 ~ 10,
      TRUE ~ 0
    ),
    
    # ECR bonus - better ECR (lower number) gets higher bonus
    # Scale based on the actual ECR range in our dataset
    ecr_range_min = min(ecr, na.rm = TRUE),
    ecr_range_max = max(ecr, na.rm = TRUE),
    ecr_score = round(20 - ((ecr - ecr_range_min) / (ecr_range_max - ecr_range_min)) * 20, 0),
    
    # Calculate total sleeper score
    sleeper_score = draft_value + performance_score + age_score + ecr_score
  ) %>%
  arrange(desc(sleeper_score), ecr) %>%
  mutate(
    sleeper_rank = row_number(),
    season = year
  )

# Select final columns for output
final_sleepers <- sleepers %>%
  select(
    sleeper_rank,
    player,
    position,
    team,
    age,
    college,
    draft_number,
    games_played_prev = games,
    ppg_prev = ppg,
    snap_pct_y1 = avg_snap_pct_y1,
    snap_pct_y2 = avg_snap_pct_y2,
    snap_pct_change,
    y2_snap_share_change,
    games_y2 = total_games_y2,
    sleeper_score,
    ecr,
    season
  ) %>%
  mutate(
    sleeper_score = round(sleeper_score, 0),
    snap_pct_change = round(snap_pct_change, 1),
    y2_snap_share_change = round(y2_snap_share_change, 2)
  )

# Add random sleep before saving
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds before saving results...\n")
Sys.sleep(sleep_time)

# Save to CSV
write_csv(final_sleepers, output_file)

# Create PNG table visualization
cat("\nGenerating table visualization...\n")

# Generate PNG filename
png_file <- sub("\\.csv$", ".png", output_file)
if (png_file == output_file) {
  png_file <- paste0(output_file, ".png")
}

# Create formatted table
table_viz <- final_sleepers %>%
  head(30) %>%  # Show top 30 sleepers
  gt() %>%
  tab_header(
    title = paste("Second-Year Fantasy Sleepers -", year, "Season"),
    subtitle = "Second-year RB/WR/TE players from FantasyPros ECR rankings (101+, QBs excluded)"
  ) %>%
  cols_label(
    sleeper_rank = "Rank",
    player = "Player",
    position = "Pos",
    team = "Team",
    age = "Age",
    college = "College",
    draft_number = "Draft #",
    games_played_prev = "Games Y1",
    ppg_prev = "PPG Y1",
    snap_pct_y1 = "Snap% Y1",
    snap_pct_y2 = "Snap% Y2",
    snap_pct_change = "Snap Δ",
    y2_snap_share_change = "Y2 Avg Δ",
    games_y2 = "Games Y2",
    sleeper_score = "Score",
    ecr = "ECR",
    season = "Season"
  ) %>%
  fmt_number(
    columns = c(ppg_prev, snap_pct_y1, snap_pct_y2, snap_pct_change, y2_snap_share_change),
    decimals = 1
  ) %>%
  fmt_missing(
    columns = everything(),
    missing_text = "UDFA"
  ) %>%
  data_color(
    columns = sleeper_score,
    colors = scales::col_numeric(
      palette = c("lightblue", "yellow", "lightgreen"),
      domain = c(min(final_sleepers$sleeper_score), max(final_sleepers$sleeper_score))
    )
  ) %>%
  data_color(
    columns = ecr,
    colors = scales::col_numeric(
      palette = c("green", "yellow", "orange"),
      domain = c(min(final_sleepers$ecr, na.rm = TRUE), 
                 max(final_sleepers$ecr, na.rm = TRUE))
    )
  ) %>%
  tab_style(
    style = list(
      cell_fill(color = "#E8F5E9"),
      cell_text(weight = "bold")
    ),
    locations = cells_body(
      rows = sleeper_rank <= 5
    )
  ) %>%
  tab_style(
    style = cell_borders(
      sides = "bottom",
      color = "gray",
      weight = px(1)
    ),
    locations = cells_body(
      rows = everything()
    )
  ) %>%
  tab_options(
    table.font.size = 11,
    heading.title.font.size = 16,
    heading.subtitle.font.size = 14,
    table.width = pct(100)
  ) %>%
  cols_align(
    align = "center",
    columns = c(sleeper_rank, position, team, age, draft_number, games_played_prev, games_y2)
  ) %>%
  cols_align(
    align = "left",
    columns = c(player, college)
  ) %>%
  cols_align(
    align = "right",
    columns = c(ppg_prev, snap_pct_y1, snap_pct_y2, snap_pct_change, y2_snap_share_change, sleeper_score, ecr)
  ) %>%
  cols_hide(
    columns = c(season)  # Hide season column in display
  )

# Save as PNG with rate limiting
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds before saving PNG...\n")
Sys.sleep(sleep_time)

# Save the table as PNG
gtsave(table_viz, png_file, expand = 10)

# Print summary
cat("\n=== Second-Year Sleepers for", year, "Season (RB/WR/TE only) ===\n")
cat("Total second-year sleepers identified:", nrow(final_sleepers), "\n")
cat("[DEBUG] Script completed at:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n\n")

if (nrow(final_sleepers) > 0) {
  cat("Top 10 Sleepers (by sleeper score):\n")
  print(final_sleepers %>%
        head(10) %>%
        select(sleeper_rank, player, position, team, ppg_prev, snap_pct_y1, snap_pct_y2, snap_pct_change, y2_snap_share_change, sleeper_score, ecr))
  
  # Position breakdown
  cat("\nSleepers by Position:\n")
  position_summary <- final_sleepers %>%
    group_by(position) %>%
    summarise(
      count = n(),
      avg_score = round(mean(sleeper_score, na.rm = TRUE), 1),
      avg_ecr = round(mean(ecr, na.rm = TRUE), 0),
      .groups = "drop"
    ) %>%
    arrange(desc(count))
  print(position_summary)
  
  # Team breakdown
  cat("\nTop 5 Teams with Most Sleepers:\n")
  team_summary <- final_sleepers %>%
    group_by(team) %>%
    summarise(
      count = n(),
      players = paste(player, collapse = ", "),
      .groups = "drop"
    ) %>%
    arrange(desc(count)) %>%
    head(5)
  print(team_summary)
} else {
  cat("No second-year sleepers found with ECR > 100 (excluding QBs)\n")
}

cat("\nResults saved to:\n")
cat("  CSV:", output_file, "\n")
cat("  PNG:", png_file, "\n")