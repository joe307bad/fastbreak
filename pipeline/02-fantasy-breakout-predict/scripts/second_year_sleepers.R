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

# Check for the --exclude-current-week-fp flag
exclude_current_fp <- FALSE
if ("--exclude-current-week-fp" %in% args) {
  exclude_current_fp <- TRUE
  # Remove the flag from args
  args <- args[args != "--exclude-current-week-fp"]
}

# Check if correct number of arguments provided
if (length(args) < 2 || length(args) > 3) {
  cat("Usage: Rscript second_year_sleepers.R [year] [week] [output_file_prefix (optional)] [--exclude-current-week-fp]\n")
  cat("Example: Rscript second_year_sleepers.R 2024 10\n")
  cat("Example: Rscript second_year_sleepers.R 2024 10 custom_sleepers\n")
  cat("Example: Rscript second_year_sleepers.R 2024 3 --exclude-current-week-fp\n")
  quit(status = 1)
}

year <- as.integer(args[1])
week <- as.integer(args[2])

# Generate output file name with week included
if (length(args) == 3) {
  output_prefix <- args[3]
  # Remove .csv extension if user provided it in the prefix
  output_prefix <- sub("\\.csv$", "", output_prefix)
} else {
  output_prefix <- "second_year_sleepers"
}
output_file <- paste0(output_prefix, "_", year, "_w", week, ".csv")

# Validate year
current_year <- as.integer(format(Sys.Date(), "%Y"))
if (year < 2020 || year > current_year) {
  cat("Error: Year must be between 2020 and", current_year, "\n")
  quit(status = 1)
}

# Validate week
if (week < 1 || week > 18) {
  cat("Error: Week must be between 1 and 18\n")
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
cat("Loading current season (Year 2) snap count data up to week", week, "...\n")
cat("[DEBUG] Fetching snap counts for year", year, "up to week", week, "at:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n")
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds...\n")
Sys.sleep(sleep_time)

# First get weekly snap data for Year 2 to calculate week-to-week changes
year2_weekly_snaps <- load_snap_counts(year) %>%
  filter(game_type == "REG", week <= !!week) %>%  # Filter to include only weeks up to the specified week
  arrange(pfr_player_id, week) %>%
  group_by(pfr_player_id, player, position, team) %>%
  mutate(
    # Calculate the change in snap share from week to week
    snap_pct_lag = lag(offense_pct),
    weekly_snap_change = offense_pct - snap_pct_lag
  ) %>%
  ungroup()

# Calculate 2-week sliding window snap % delta for recent trend analysis
# For week 8, this looks at weeks 6-7 and 7-8 deltas and averages them
sliding_window_snaps <- NULL
if (week >= 3) {  # Need at least 3 weeks to calculate 2-week window
  # Define the sliding window: look at previous 2 weeks relative to current week
  window_weeks <- c(week - 2, week - 1, week)

  sliding_window_snaps <- year2_weekly_snaps %>%
    filter(week %in% window_weeks) %>%
    arrange(pfr_player_id, week) %>%
    group_by(pfr_player_id, player, position, team) %>%
    mutate(
      # Calculate week-to-week snap % changes within the window
      snap_pct_prev = lag(offense_pct),
      window_snap_delta = offense_pct - snap_pct_prev
    ) %>%
    # Calculate average delta over the 2-week sliding window
    summarise(
      sliding_window_avg_delta = round(mean(window_snap_delta, na.rm = TRUE), 2),
      window_weeks_played = sum(!is.na(window_snap_delta)),
      .groups = "drop"
    ) %>%
    # Only keep players who played in at least 1 of the delta calculations
    filter(window_weeks_played > 0) %>%
    mutate(clean_snap_name = clean_name(player))

  cat("[DEBUG] Calculated sliding window snap deltas for", nrow(sliding_window_snaps), "players\n")
  cat("[DEBUG] Window: weeks", paste(window_weeks, collapse = ", "), "(analyzing deltas between consecutive weeks)\n")
} else {
  cat("[DEBUG] Week", week, "too early for sliding window analysis (need week 3+)\n")
}

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

# Join sliding window data if available
if (!is.null(sliding_window_snaps) && nrow(sliding_window_snaps) > 0) {
  current_year_snaps <- current_year_snaps %>%
    left_join(
      sliding_window_snaps %>% select(clean_snap_name, sliding_window_avg_delta),
      by = "clean_snap_name"
    ) %>%
    mutate(sliding_window_avg_delta = coalesce(sliding_window_avg_delta, 0))
} else {
  current_year_snaps <- current_year_snaps %>%
    mutate(sliding_window_avg_delta = 0)
}

cat("[DEBUG] Loaded", nrow(current_year_snaps), "players' Year 2 snap counts\n")

# Load fantasy points data
if (!exclude_current_fp) {
  # Load current and previous week when flag is NOT set
  cat("Loading fantasy points data for weeks", week-1, "and", week, "...\n")
  cat("[DEBUG] Fetching fantasy points data at:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n")
  sleep_time <- runif(1, min = 1, max = 3)
  cat("Waiting", round(sleep_time, 2), "seconds...\n")
  Sys.sleep(sleep_time)

  # Get Year 2 weekly fantasy points data
  year2_fantasy_points <- load_player_stats(year) %>%
    filter(season_type == "REG", week %in% c(max(1, !!week - 1), !!week)) %>%
    select(player_id, player_display_name, position, week, fantasy_points, fantasy_points_ppr) %>%
    pivot_wider(
      id_cols = c(player_id, player_display_name, position),
      names_from = week,
      values_from = c(fantasy_points, fantasy_points_ppr),
      names_prefix = "week_"
    )

  # Calculate fantasy points with safer column access
  prev_week_col <- paste0("fantasy_points_ppr_week_", max(1, week - 1))
  current_week_col <- paste0("fantasy_points_ppr_week_", week)

  year2_fantasy_points <- year2_fantasy_points %>%
    mutate(
      # Get previous and current week fantasy points (PPR scoring) with safer access
      prev_week_fp = if(prev_week_col %in% names(.)) get(prev_week_col) else 0,
      current_week_fp = if(current_week_col %in% names(.)) get(current_week_col) else 0,
      prev_week_fp = coalesce(prev_week_fp, 0),
      current_week_fp = coalesce(current_week_fp, 0),
      fp_delta = current_week_fp - prev_week_fp,
      clean_fp_name = clean_name(player_display_name)
    ) %>%
    select(player_id, clean_fp_name, position, prev_week_fp, current_week_fp, fp_delta)

  cat("[DEBUG] Loaded fantasy points for", nrow(year2_fantasy_points), "players\n")
} else {
  # Load all weeks PRIOR to the specified week when flag IS set
  if (week > 1) {
    cat("Loading fantasy points data for weeks 1 through", week - 1, "(excluding week", week, ")\n")
    cat("[DEBUG] Fetching historical fantasy points data at:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n")
    sleep_time <- runif(1, min = 1, max = 3)
    cat("Waiting", round(sleep_time, 2), "seconds...\n")
    Sys.sleep(sleep_time)

    # Get Year 2 weekly fantasy points data for all weeks up to (not including) the specified week
    year2_fantasy_points <- load_player_stats(year) %>%
      filter(season_type == "REG", week < !!week) %>%
      select(player_id, player_display_name, position, week, fantasy_points_ppr) %>%
      pivot_wider(
        id_cols = c(player_id, player_display_name, position),
        names_from = week,
        values_from = fantasy_points_ppr,
        names_prefix = "w",
        values_fill = 0
      ) %>%
      mutate(clean_fp_name = clean_name(player_display_name))

    # Calculate average and total fantasy points
    fp_cols <- grep("^w[0-9]+$", names(year2_fantasy_points), value = TRUE)
    if (length(fp_cols) > 0) {
      year2_fantasy_points <- year2_fantasy_points %>%
        mutate(
          total_fp = rowSums(select(., all_of(fp_cols)), na.rm = TRUE),
          avg_fp = round(total_fp / length(fp_cols), 1),
          games_played = rowSums(select(., all_of(fp_cols)) > 0, na.rm = TRUE)
        )
    }

    cat("[DEBUG] Loaded fantasy points for", nrow(year2_fantasy_points), "players across", length(fp_cols), "weeks\n")
  } else {
    cat("No prior weeks available (week 1 specified)\n")
    # Create empty dataframe with minimal structure
    year2_fantasy_points <- data.frame(
      player_id = character(),
      clean_fp_name = character(),
      position = character()
    )
  }
}

# Load defense rankings if available
cat("Checking for defense rankings data...\n")
defense_rankings_file <- paste0("defense_rankings_", year, ".csv")
defense_rankings <- NULL

if (file.exists(defense_rankings_file)) {
  cat("Found", defense_rankings_file, "- loading defense rankings...\n")
  defense_rankings <- read_csv(defense_rankings_file, show_col_types = FALSE)
  cat("Loaded defense rankings for", nrow(defense_rankings), "teams\n")
} else {
  cat("No defense rankings file found. Continuing without matchup adjustments.\n")
}

# Load roster and injury data once for the season
cat("Loading roster and injury data for", year, "season...\n")
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds...\n")
Sys.sleep(sleep_time)

# Load data once at the beginning
rosters_data <- NULL
injuries_data <- NULL

tryCatch({
  rosters_data <- load_rosters(seasons = year)
  cat("Loaded", nrow(rosters_data), "roster entries for", year, "season\n")
  cat("[DEBUG] Roster data columns:", paste(names(rosters_data), collapse = ", "), "\n")
}, error = function(e) {
  cat("Warning: Could not load roster data:", e$message, "\n")
  rosters_data <<- NULL
})

tryCatch({
  injuries_data <- load_injuries(seasons = year)
  cat("Loaded", nrow(injuries_data), "injury entries for", year, "season\n")
  cat("[DEBUG] Injury data columns:", paste(names(injuries_data), collapse = ", "), "\n")
}, error = function(e) {
  cat("Warning: Could not load injury data:", e$message, "\n")
  injuries_data <<- NULL
})

# Create simple lookup vector for active roster players
active_players <- NULL
if (!is.null(rosters_data)) {
  active_players <- unique(rosters_data$full_name)
  active_players <- active_players[!is.na(active_players)]
  cat("Created active player lookup with", length(active_players), "unique player names\n")
}

# Function to check player status for a given week
check_player_status <- function(player_name, check_week,
                               active_roster = active_players,
                               injury_data = injuries_data) {

  # Check if on active roster
  on_roster <- !is.null(active_roster) && player_name %in% active_roster

  if (!on_roster) {
    return(list(
      on_roster = FALSE,
      injury_status = "Not on roster",
      available = FALSE
    ))
  }

  # Check injury status for that week
  if (!is.null(injury_data)) {
    player_injury <- injury_data %>%
      filter(
        full_name == player_name,
        week == !!check_week
      )

    if (nrow(player_injury) == 0) {
      # No injury report = healthy/available
      return(list(
        on_roster = TRUE,
        injury_status = "Healthy",
        available = TRUE
      ))
    } else {
      injury_status <- player_injury$report_status[1]  # Take first if multiple entries

      # Determine availability based on injury status
      available <- case_when(
        tolower(injury_status) %in% c("out", "ir", "injured reserve", "pup", "o") ~ FALSE,
        tolower(injury_status) %in% c("questionable", "doubtful", "q", "d") ~ FALSE,  # Conservative approach
        TRUE ~ TRUE  # probable, healthy, etc.
      )

      return(list(
        on_roster = TRUE,
        injury_status = injury_status,
        available = available
      ))
    }
  } else {
    # No injury data available, assume healthy if on roster
    return(list(
      on_roster = TRUE,
      injury_status = "Unknown",
      available = TRUE
    ))
  }
}

# Load NFL schedule for the current week to get next opponents
cat("Loading NFL schedule for week", week, "...\n")
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds...\n")
Sys.sleep(sleep_time)

# Get schedule data for the specific week
weekly_schedule <- tryCatch({
  # Load the full season schedule
  fast_scraper_schedules(year) %>%
    filter(week == !!week) %>%
    select(week, game_id, home_team, away_team)
}, error = function(e) {
  cat("Warning: Could not load schedule data:", e$message, "\n")
  cat("Continuing without matchup data.\n")
  NULL
})

# Create opponent mapping for the week
opponent_mapping <- NULL
if (!is.null(weekly_schedule)) {
  # Create bidirectional mapping of opponents
  home_opponents <- weekly_schedule %>%
    select(team = home_team, opponent = away_team, week)

  away_opponents <- weekly_schedule %>%
    select(team = away_team, opponent = home_team, week)

  opponent_mapping <- bind_rows(home_opponents, away_opponents) %>%
    distinct()

  cat("Loaded opponents for", nrow(opponent_mapping), "teams in week", week, "\n")
}

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
cat("[DEBUG] Second year rosters columns:", paste(names(second_year_rosters), collapse = ", "), "\n")

sleeper_candidates <- second_year_rosters %>%
  inner_join(
    ecr_with_clean_names %>% select(clean_name, position, player_ecr = player, ecr),
    by = c("clean_name", "position")
  )

cat("[DEBUG] Sleeper candidates columns after ECR join:", paste(names(sleeper_candidates), collapse = ", "), "\n")

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
    current_year_snaps %>% select(clean_snap_name, position, avg_snap_pct_y2, total_games_y2, w1_snap_share, y2_snap_share_change, sliding_window_avg_delta),
    by = c("clean_name" = "clean_snap_name", "position")
  ) %>%
  mutate(
    avg_snap_pct_y2 = coalesce(avg_snap_pct_y2, 0),
    total_games_y2 = coalesce(total_games_y2, 0),
    w1_snap_share = coalesce(w1_snap_share, 0),
    y2_snap_share_change = coalesce(y2_snap_share_change, 0),
    sliding_window_avg_delta = coalesce(sliding_window_avg_delta, 0),
    # Calculate snap percentage change from Y1 to Y2
    snap_pct_change = avg_snap_pct_y2 - avg_snap_pct_y1
  )

# Join with Year 2 fantasy points data
if (nrow(year2_fantasy_points) > 0) {
  if (!exclude_current_fp) {
    # Include current week FP columns when not excluded
    sleeper_candidates <- sleeper_candidates %>%
      left_join(
        year2_fantasy_points %>% select(-position),  # Remove position to avoid conflict
        by = c("player_id" = "player_id")
      ) %>%
      mutate(
        prev_week_fp = coalesce(prev_week_fp, 0),
        current_week_fp = coalesce(current_week_fp, 0),
        fp_delta = coalesce(fp_delta, 0)
      )
  } else {
    # When excluding current week FP, still join weekly columns but not summary columns
    # Get column names to join (exclude summary columns)
    fp_weekly_cols <- grep("^w[0-9]+$", names(year2_fantasy_points), value = TRUE)
    join_cols <- c("player_id", "player_display_name", fp_weekly_cols)
    if ("total_fp" %in% names(year2_fantasy_points)) {
      join_cols <- c(join_cols, "total_fp", "avg_fp", "games_played")
    }

    sleeper_candidates <- sleeper_candidates %>%
      left_join(
        year2_fantasy_points %>% select(all_of(join_cols)),
        by = c("player_id" = "player_id")
      )

    # Fill missing weekly FP columns with 0 for players not in fantasy data
    fp_weekly_cols <- grep("^w[0-9]+$", names(sleeper_candidates), value = TRUE)
    if (length(fp_weekly_cols) > 0) {
      sleeper_candidates <- sleeper_candidates %>%
        mutate(across(all_of(fp_weekly_cols), ~ coalesce(.x, 0)))
    }

    # Fill missing summary FP columns with 0
    summary_cols <- c("total_fp", "avg_fp", "games_played")
    existing_summary_cols <- summary_cols[summary_cols %in% names(sleeper_candidates)]
    if (length(existing_summary_cols) > 0) {
      sleeper_candidates <- sleeper_candidates %>%
        mutate(across(all_of(existing_summary_cols), ~ coalesce(.x, 0)))
    }
  }
} else {
  if (!exclude_current_fp) {
    # Add empty columns if no fantasy points data available
    sleeper_candidates <- sleeper_candidates %>%
      mutate(
        prev_week_fp = 0,
        current_week_fp = 0,
        fp_delta = 0
      )
  }
}

# Add opponent and defensive matchup data
if (!is.null(opponent_mapping)) {
  sleeper_candidates <- sleeper_candidates %>%
    left_join(
      opponent_mapping %>% select(team, opponent),
      by = "team"
    ) %>%
    mutate(
      opponent = coalesce(opponent, "BYE")
    )

  cat("[DEBUG] Added opponents for sleeper candidates\n")

  # If defense rankings exist, join them and calculate matchup scores
  if (!is.null(defense_rankings)) {
    sleeper_candidates <- sleeper_candidates %>%
      left_join(
        defense_rankings %>%
          select(team, rush_defense_rank, pass_defense_rank),
        by = c("opponent" = "team")
      ) %>%
      mutate(
        # Get the relevant defensive rank based on position
        relevant_def_rank = case_when(
          position == "RB" ~ rush_defense_rank,
          position %in% c("WR", "TE") ~ pass_defense_rank,
          TRUE ~ NA_real_
        ),
        # Calculate matchup score (0-30 points)
        # Higher defensive rank (worse defense) = better matchup for offense
        matchup_score = case_when(
          is.na(relevant_def_rank) ~ 0,  # No matchup data
          relevant_def_rank >= 28 ~ 30,  # Elite matchup (bottom 5 defense)
          relevant_def_rank >= 24 ~ 25,  # Great matchup
          relevant_def_rank >= 20 ~ 20,  # Good matchup
          relevant_def_rank >= 16 ~ 15,  # Above average matchup
          relevant_def_rank >= 13 ~ 10,  # Average matchup
          relevant_def_rank >= 9 ~ 5,    # Below average matchup
          relevant_def_rank >= 5 ~ 2,    # Tough matchup
          TRUE ~ 0                        # Elite defense (top 4)
        )
      )

    cat("[DEBUG] Added defensive matchup scores\n")
  } else {
    # No defense rankings, set default values
    sleeper_candidates <- sleeper_candidates %>%
      mutate(
        rush_defense_rank = NA_real_,
        pass_defense_rank = NA_real_,
        relevant_def_rank = NA_real_,
        matchup_score = 0
      )
  }
} else {
  # No opponent mapping, set default values
  sleeper_candidates <- sleeper_candidates %>%
    mutate(
      opponent = "N/A",
      rush_defense_rank = NA_real_,
      pass_defense_rank = NA_real_,
      relevant_def_rank = NA_real_,
      matchup_score = 0
    )
}

# Note: Keep clean_name column for now - will remove after filtering

cat("[DEBUG] Sleeper candidates columns after all joins:", paste(names(sleeper_candidates), collapse = ", "), "\n")

# Filter out injured and practice squad players using improved nflreadr approach
if (!is.null(active_players) || !is.null(injuries_data)) {
  initial_count <- nrow(sleeper_candidates)

  # Apply player status filtering
  sleeper_candidates <- sleeper_candidates %>%
    rowwise() %>%
    mutate(
      player_available = check_player_status(player, week, active_players, injuries_data)$available
    ) %>%
    ungroup() %>%
    filter(player_available) %>%
    select(-player_available)

  filtered_count <- nrow(sleeper_candidates)
  cat("[DEBUG] Player status filtering: Started with", initial_count, "candidates\n")
  cat("[DEBUG] After filtering out injured/practice squad players:", filtered_count, "candidates remain\n")
  cat("[DEBUG] Excluded", initial_count - filtered_count, "players due to roster/injury status in week", week, "\n")
} else {
  cat("[DEBUG] No roster or injury data available - skipping player status filtering\n")
}

# Additional filtering: Require meaningful snap counts in recent weeks
# This helps identify players who are actually getting playing time
if (week >= 2) {
  initial_count <- nrow(sleeper_candidates)

  # Filter for players who have had at least 10% snap share in any of the last 2 weeks
  # or at least 5 snaps in the most recent week
  min_recent_weeks <- max(1, week - 1)
  recent_snap_players <- year2_weekly_snaps %>%
    filter(week >= min_recent_weeks & week <= !!week) %>%
    group_by(pfr_player_id, player) %>%
    summarise(
      max_recent_snap_pct = max(offense_pct, na.rm = TRUE),
      recent_snaps = sum(offense_snaps, na.rm = TRUE),
      recent_weeks_played = n(),
      .groups = "drop"
    ) %>%
    filter(max_recent_snap_pct >= 10 | (recent_snaps >= 5 & recent_weeks_played >= 1)) %>%
    mutate(clean_snap_name = clean_name(player))

  cat("[DEBUG] Recent snap players count:", nrow(recent_snap_players), "\n")
  if (nrow(recent_snap_players) > 0 && !is.null(recent_snap_players$clean_snap_name)) {
    # Additional safety check for the clean_snap_name column
    valid_snap_names <- recent_snap_players$clean_snap_name[!is.na(recent_snap_players$clean_snap_name)]
    cat("[DEBUG] Valid snap names count:", length(valid_snap_names), "\n")

    if (length(valid_snap_names) > 0) {
      cat("[DEBUG] Sample valid snap names:", head(valid_snap_names, 3), "\n")
      cat("[DEBUG] Sample sleeper candidate names:", head(sleeper_candidates$clean_name, 3), "\n")
      cat("[DEBUG] clean_name column class:", class(sleeper_candidates$clean_name), "\n")
      cat("[DEBUG] valid_snap_names class:", class(valid_snap_names), "\n")

      # Convert to character vectors to ensure compatibility
      clean_names_vec <- as.character(sleeper_candidates$clean_name)
      valid_snap_names_vec <- as.character(valid_snap_names)

      # Find matching indices
      matching_indices <- clean_names_vec %in% valid_snap_names_vec
      sleeper_candidates <- sleeper_candidates[matching_indices, ]
    } else {
      cat("[DEBUG] No valid snap names found - skipping snap count filtering\n")
    }

    filtered_count <- nrow(sleeper_candidates)
    cat("[DEBUG] Snap count filtering: Started with", initial_count, "candidates\n")
    cat("[DEBUG] After snap count filtering:", filtered_count, "candidates remain\n")
    cat("[DEBUG] Excluded", initial_count - filtered_count, "players with minimal recent snap counts\n")
  }
}

# Remove helper columns before final processing
if ("clean_fp_name" %in% names(sleeper_candidates)) {
  sleeper_candidates <- sleeper_candidates %>%
    select(-clean_name, -clean_fp_name)
} else {
  sleeper_candidates <- sleeper_candidates %>%
    select(-clean_name)
}

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

    # Sliding window snap trend bonus (higher weight for increasing snap counts)
    # Scale: positive deltas get up to 15 points, negative deltas get 0
    sliding_window_score = case_when(
      sliding_window_avg_delta >= 10 ~ 15,   # Major snap count increase
      sliding_window_avg_delta >= 5 ~ 12,    # Significant increase
      sliding_window_avg_delta >= 2 ~ 8,     # Moderate increase
      sliding_window_avg_delta > 0 ~ 5,      # Small increase
      sliding_window_avg_delta >= -2 ~ 2,    # Stable/slight decrease
      TRUE ~ 0                               # Declining snap count
    ),

    # Calculate total sleeper score (now includes matchup + sliding window)
    # Max score increased from 160 to 175 with sliding window bonus
    sleeper_score = draft_value + performance_score + age_score + ecr_score + matchup_score + sliding_window_score
  ) %>%
  arrange(desc(sleeper_score), ecr) %>%
  mutate(
    sleeper_rank = row_number(),
    season = year
  )

# Filter out players who scored >10 fantasy points in the previous week
# These players are likely already emerging and not true "sleepers"
if (!exclude_current_fp && "prev_week_fp" %in% names(sleepers)) {
  initial_count <- nrow(sleepers)
  sleepers <- sleepers %>%
    filter(is.na(prev_week_fp) | prev_week_fp <= 10)
  filtered_count <- nrow(sleepers)
  cat("[DEBUG] Filtered out", initial_count - filtered_count, "players with >10 FP in previous week\n")
  cat("[DEBUG] Remaining sleeper candidates:", filtered_count, "\n")

  # Recalculate rankings after filtering
  sleepers <- sleepers %>%
    mutate(sleeper_rank = row_number())
} else if (exclude_current_fp) {
  # When using exclude flag, filter based on the last available week
  # For week 3 analysis, filter out players with >10 FP in week 2
  prev_week_col <- paste0("w", week - 1)
  if (prev_week_col %in% names(sleepers)) {
    initial_count <- nrow(sleepers)
    sleepers <- sleepers %>%
      filter(is.na(!!sym(prev_week_col)) | !!sym(prev_week_col) <= 10)
    filtered_count <- nrow(sleepers)
    cat("[DEBUG] Filtered out", initial_count - filtered_count, "players with >10 FP in", prev_week_col, "\n")
    cat("[DEBUG] Remaining sleeper candidates:", filtered_count, "\n")

    # Recalculate rankings after filtering
    sleepers <- sleepers %>%
      mutate(sleeper_rank = row_number())
  }
}

# Debug: Check what columns are available in sleepers before final selection
cat("[DEBUG] Sleepers columns before final select:", paste(names(sleepers), collapse = ", "), "\n")

# Select final columns for output
if (!exclude_current_fp) {
  final_sleepers <- sleepers %>%
    select(
      sleeper_rank,
      player,
      position,
      team,
      opponent,
      games_played_prev = games,
      ppg_prev = ppg,
      snap_pct_y1 = avg_snap_pct_y1,
      snap_pct_y2 = avg_snap_pct_y2,
      snap_pct_change,
      y2_snap_share_change,
      sliding_window_avg_delta,
      games_y2 = total_games_y2,
      prev_week_fp,
      current_week_fp,
      fp_delta,
      sleeper_score,
      ecr,
      season
    ) %>%
    mutate(
      analysis_week = !!week,
      sleeper_score = round(sleeper_score, 0),
      snap_pct_change = round(snap_pct_change, 1),
      y2_snap_share_change = round(y2_snap_share_change, 2),
      sliding_window_avg_delta = round(sliding_window_avg_delta, 2),
      prev_week_fp = round(prev_week_fp, 1),
      current_week_fp = round(current_week_fp, 1),
      fp_delta = round(fp_delta, 1),
      hit_status = ifelse(fp_delta > 5, "HIT", "")
    )
} else {
  # When excluding current week FP, also exclude matchup columns and include historical FP data
  base_cols <- c("sleeper_rank", "player", "position", "team", "opponent",
                 "games_played_prev", "ppg_prev", "avg_snap_pct_y1", "avg_snap_pct_y2",
                 "snap_pct_change", "y2_snap_share_change", "sliding_window_avg_delta",
                 "games_y2", "sleeper_score", "ecr", "season")

  # Get any weekly FP columns that exist in the data
  fp_weekly_cols <- grep("^w[0-9]+$", names(sleepers), value = TRUE)
  summary_fp_cols <- c("total_fp", "avg_fp", "games_played")
  summary_fp_cols <- summary_fp_cols[summary_fp_cols %in% names(sleepers)]

  all_cols <- c(base_cols, fp_weekly_cols, summary_fp_cols)
  all_cols <- all_cols[all_cols %in% names(sleepers)]  # Only select columns that exist

  final_sleepers <- sleepers %>%
    select(all_of(all_cols)) %>%
    mutate(
      analysis_week = !!week,
      sleeper_score = round(sleeper_score, 0),
      snap_pct_change = round(snap_pct_change, 1),
      y2_snap_share_change = round(y2_snap_share_change, 2),
      sliding_window_avg_delta = round(sliding_window_avg_delta, 2)
    )

  # Note: Include all players, even UDFAs with 0.0 fantasy points

  # Round weekly FP columns if they exist
  if (length(fp_weekly_cols) > 0) {
    final_sleepers <- final_sleepers %>%
      mutate(across(all_of(fp_weekly_cols), ~ round(.x, 1)))
  }

  # Round summary FP columns if they exist
  if ("total_fp" %in% names(final_sleepers)) {
    final_sleepers <- final_sleepers %>%
      mutate(total_fp = round(total_fp, 1))
  }
}

# Debug after select and mutate
cat("[DEBUG] Final sleepers columns after select and mutate:", paste(names(final_sleepers), collapse = ", "), "\n")

# Add random sleep before saving
sleep_time <- runif(1, min = 1, max = 3)
cat("Waiting", round(sleep_time, 2), "seconds before saving results...\n")
Sys.sleep(sleep_time)

# Save to CSV
write_csv(final_sleepers, output_file)

# Create PNG table visualization
cat("\nGenerating table visualization...\n")

# Debug: Check final_sleepers structure
cat("[DEBUG] Final sleepers columns:", paste(names(final_sleepers), collapse = ", "), "\n")
cat("[DEBUG] Final sleepers rows:", nrow(final_sleepers), "\n")

# Generate PNG filename
png_file <- sub("\\.csv$", ".png", output_file)
if (png_file == output_file) {
  png_file <- paste0(output_file, ".png")
}

# Only create table if we have data
if (nrow(final_sleepers) > 0) {
  # Create formatted table
  table_viz <- final_sleepers %>%
    head(30) %>%  # Show top 30 sleepers
    gt() %>%
  tab_header(
    title = paste("Second-Year Fantasy Sleepers -", year, "Season (through Week", week, ")"),
    subtitle = paste("Second-year RB/WR/TE players from FantasyPros ECR rankings (101+, QBs excluded)")
  )

  # Apply column labels based on whether FP columns are included
  if (!exclude_current_fp) {
    table_viz <- table_viz %>%
      cols_label(
        sleeper_rank = "Rank",
        player = "Player",
        position = "Pos",
        team = "Team",
        opponent = "Opp",
        games_played_prev = "Games Y1",
        ppg_prev = "PPG Y1",
        snap_pct_y1 = "Snap% Y1",
        snap_pct_y2 = "Snap% Y2",
        snap_pct_change = "Snap Δ",
        y2_snap_share_change = "Y2 Avg Δ",
        sliding_window_avg_delta = "2W Trend",
        games_y2 = "Games Y2",
        prev_week_fp = paste("W", week-1, "FP"),
        current_week_fp = paste("W", week, "FP"),
        fp_delta = "FP Δ",
        hit_status = "HIT",
        sleeper_score = "Score",
        ecr = "ECR",
        season = "Season",
        analysis_week = "Week"
      )
  } else {
    # Create base labels only for columns that exist in final dataset
    base_labels <- list()

    # Only add labels for columns that exist
    if ("sleeper_rank" %in% names(final_sleepers)) {
      base_labels[["sleeper_rank"]] <- "Rank"
    }
    if ("player" %in% names(final_sleepers)) {
      base_labels[["player"]] <- "Player"
    }
    if ("position" %in% names(final_sleepers)) {
      base_labels[["position"]] <- "Pos"
    }
    if ("team" %in% names(final_sleepers)) {
      base_labels[["team"]] <- "Team"
    }
    if ("opponent" %in% names(final_sleepers)) {
      base_labels[["opponent"]] <- "Opp"
    }
    if ("games_played_prev" %in% names(final_sleepers)) {
      base_labels[["games_played_prev"]] <- "Games Y1"
    }
    if ("ppg_prev" %in% names(final_sleepers)) {
      base_labels[["ppg_prev"]] <- "PPG Y1"
    }
    if ("avg_snap_pct_y1" %in% names(final_sleepers)) {
      base_labels[["avg_snap_pct_y1"]] <- "Y1 Snap%"
    }
    if ("avg_snap_pct_y2" %in% names(final_sleepers)) {
      base_labels[["avg_snap_pct_y2"]] <- "Y2 Snap%"
    }
    if ("snap_pct_change" %in% names(final_sleepers)) {
      base_labels[["snap_pct_change"]] <- "Snap Δ"
    }
    if ("y2_snap_share_change" %in% names(final_sleepers)) {
      base_labels[["y2_snap_share_change"]] <- "Y2 Avg Δ"
    }
    if ("sliding_window_avg_delta" %in% names(final_sleepers)) {
      base_labels[["sliding_window_avg_delta"]] <- "2W Trend"
    }
    if ("games_y2" %in% names(final_sleepers)) {
      base_labels[["games_y2"]] <- "Games Y2"
    }
    if ("sleeper_score" %in% names(final_sleepers)) {
      base_labels[["sleeper_score"]] <- "Score"
    }
    if ("ecr" %in% names(final_sleepers)) {
      base_labels[["ecr"]] <- "ECR"
    }
    if ("season" %in% names(final_sleepers)) {
      base_labels[["season"]] <- "Season"
    }
    if ("analysis_week" %in% names(final_sleepers)) {
      base_labels[["analysis_week"]] <- "Week"
    }

    # Add weekly FP column labels if they exist
    fp_weekly_cols <- grep("^w[0-9]+$", names(final_sleepers), value = TRUE)
    if (length(fp_weekly_cols) > 0) {
      fp_labels <- setNames(
        paste("W", gsub("^w", "", fp_weekly_cols), sep = ""),
        fp_weekly_cols
      )
      base_labels <- c(base_labels, fp_labels)
    }

    # Add summary FP column labels if they exist
    if ("total_fp" %in% names(final_sleepers)) {
      base_labels[["total_fp"]] <- "Total FP"
    }
    if ("avg_fp" %in% names(final_sleepers)) {
      base_labels[["avg_fp"]] <- "Avg FP"
    }
    if ("games_played" %in% names(final_sleepers)) {
      base_labels[["games_played"]] <- "Games"
    }

    table_viz <- table_viz %>%
      cols_label(.list = base_labels)
  }

  # Format numbers based on available columns
  if (!exclude_current_fp) {
    numeric_cols <- c("ppg_prev", "snap_pct_y1", "snap_pct_y2",
                      "snap_pct_change", "y2_snap_share_change",
                      "prev_week_fp", "current_week_fp", "fp_delta")
    numeric_cols <- numeric_cols[numeric_cols %in% names(final_sleepers)]
    if (length(numeric_cols) > 0) {
      table_viz <- table_viz %>%
        fmt_number(
          columns = all_of(numeric_cols),
          decimals = 1
        )
    }
  } else {
    numeric_cols <- c("ppg_prev", "avg_snap_pct_y1", "avg_snap_pct_y2",
                      "snap_pct_change", "y2_snap_share_change",
                      "sliding_window_avg_delta")
    numeric_cols <- numeric_cols[numeric_cols %in% names(final_sleepers)]

    # Add weekly FP columns for formatting
    fp_weekly_cols <- grep("^w[0-9]+$", names(final_sleepers), value = TRUE)
    if (length(fp_weekly_cols) > 0) {
      numeric_cols <- c(numeric_cols, fp_weekly_cols)
    }

    # Add summary FP columns for formatting
    summary_cols <- c("total_fp", "avg_fp")
    summary_cols <- summary_cols[summary_cols %in% names(final_sleepers)]
    numeric_cols <- c(numeric_cols, summary_cols)

    if (length(numeric_cols) > 0) {
      table_viz <- table_viz %>%
        fmt_number(
          columns = all_of(numeric_cols),
          decimals = 1
        )
    }
  }

  table_viz <- table_viz %>%
    fmt_missing(
      columns = everything(),
      missing_text = "-"
    )

  # Apply FP delta coloring only if the column exists
  if (!exclude_current_fp) {
    table_viz <- table_viz %>%
      data_color(
        columns = fp_delta,
        colors = scales::col_numeric(
          palette = c("white", "lightblue", "blue", "darkblue"),
          domain = c(min(final_sleepers$fp_delta, na.rm = TRUE),
                     max(final_sleepers$fp_delta, na.rm = TRUE))
        )
      )
  }

  # Apply defense rank and matchup score coloring only if columns exist
  if ("relevant_def_rank" %in% names(final_sleepers)) {
    table_viz <- table_viz %>%
      data_color(
        columns = relevant_def_rank,
        colors = scales::col_numeric(
          palette = c("#006400", "#228B22", "#90EE90", "#FFFACD",
                      "#FFB347", "#FF6347", "#DC143C"),
          domain = c(1, 32),
          na.color = "lightgray"
        )
      )
  }

  if ("matchup_score" %in% names(final_sleepers)) {
    table_viz <- table_viz %>%
      data_color(
        columns = matchup_score,
        colors = scales::col_numeric(
          palette = c("white", "#E8F5E9", "#66BB6A", "#43A047", "#2E7D32"),
          domain = c(0, 30)
        )
      )
  }

  # Apply HIT status styling only if FP columns are included
  if (!exclude_current_fp) {
    table_viz <- table_viz %>%
      tab_style(
        style = list(
          cell_fill(color = "red"),
          cell_text(color = "white", weight = "bold")
        ),
        locations = cells_body(
          columns = hit_status,
          rows = hit_status == "HIT"
        )
      )
  }

  # Apply top 10 shading based on available columns
  if (!exclude_current_fp) {
    styling_cols <- c("sleeper_rank", "player", "position", "team", "opponent",
                      "games_played_prev", "ppg_prev", "snap_pct_y1", "snap_pct_y2",
                      "snap_pct_change", "y2_snap_share_change",
                      "sliding_window_avg_delta", "games_y2", "prev_week_fp",
                      "current_week_fp", "sleeper_score", "ecr")
    styling_cols <- styling_cols[styling_cols %in% names(final_sleepers)]
    if (length(styling_cols) > 0) {
      table_viz <- table_viz %>%
        tab_style(
          style = cell_fill(color = "#E8F5E9"),
          locations = cells_body(
            columns = all_of(styling_cols),
            rows = sleeper_rank <= 10
          )
        )
    }
  } else {
    styling_cols <- c("sleeper_rank", "player", "position", "team", "opponent",
                      "games_played_prev", "ppg_prev", "avg_snap_pct_y1",
                      "avg_snap_pct_y2", "snap_pct_change", "y2_snap_share_change",
                      "sliding_window_avg_delta", "games_y2", "sleeper_score", "ecr")
    styling_cols <- styling_cols[styling_cols %in% names(final_sleepers)]

    # Add weekly FP columns for styling
    fp_weekly_cols <- grep("^w[0-9]+$", names(final_sleepers), value = TRUE)
    if (length(fp_weekly_cols) > 0) {
      styling_cols <- c(styling_cols, fp_weekly_cols)
    }

    # Add summary FP columns for styling
    summary_cols <- c("total_fp", "avg_fp", "games_played")
    summary_cols <- summary_cols[summary_cols %in% names(final_sleepers)]
    styling_cols <- c(styling_cols, summary_cols)

    if (length(styling_cols) > 0) {
      table_viz <- table_viz %>%
        tab_style(
          style = cell_fill(color = "#E8F5E9"),
          locations = cells_body(
            columns = all_of(styling_cols),
            rows = sleeper_rank <= 10
          )
        )
    }
  }

  table_viz <- table_viz %>%
    tab_style(
      style = cell_borders(
        sides = "bottom",
        color = "black",
        weight = px(3)
      ),
      locations = cells_body(
        rows = sleeper_rank == 10
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
    )

  # Apply column alignments based on available columns
  if (!exclude_current_fp) {
    center_cols <- c("sleeper_rank", "position", "team", "opponent",
                     "relevant_def_rank", "matchup_score", "age",
                     "draft_number", "games_played_prev", "games_y2",
                     "hit_status")
    center_cols <- center_cols[center_cols %in% names(final_sleepers)]

    left_cols <- c("player")
    left_cols <- left_cols[left_cols %in% names(final_sleepers)]

    right_cols <- c("ppg_prev", "snap_pct_y1", "snap_pct_y2", "snap_pct_change",
                    "y2_snap_share_change", "prev_week_fp", "current_week_fp",
                    "fp_delta", "sleeper_score", "ecr")
    right_cols <- right_cols[right_cols %in% names(final_sleepers)]

    if (length(center_cols) > 0) {
      table_viz <- table_viz %>%
        cols_align(align = "center", columns = all_of(center_cols))
    }
    if (length(left_cols) > 0) {
      table_viz <- table_viz %>%
        cols_align(align = "left", columns = all_of(left_cols))
    }
    if (length(right_cols) > 0) {
      table_viz <- table_viz %>%
        cols_align(align = "right", columns = all_of(right_cols))
    }
  } else {
    center_cols <- c("sleeper_rank", "position", "team", "opponent",
                     "relevant_def_rank", "matchup_score",
                     "games_played_prev", "games_y2")
    center_cols <- center_cols[center_cols %in% names(final_sleepers)]

    left_cols <- c("player")
    left_cols <- left_cols[left_cols %in% names(final_sleepers)]

    right_cols <- c("ppg_prev", "avg_snap_pct_y1", "avg_snap_pct_y2",
                    "snap_pct_change", "y2_snap_share_change", "sleeper_score",
                    "ecr")
    right_cols <- right_cols[right_cols %in% names(final_sleepers)]

    # Add weekly FP columns to right alignment
    fp_weekly_cols <- grep("^w[0-9]+$", names(final_sleepers), value = TRUE)
    if (length(fp_weekly_cols) > 0) {
      right_cols <- c(right_cols, fp_weekly_cols)
    }

    # Add summary FP columns to right alignment
    summary_cols <- c("total_fp", "avg_fp")
    summary_cols <- summary_cols[summary_cols %in% names(final_sleepers)]
    right_cols <- c(right_cols, summary_cols)

    if (length(center_cols) > 0) {
      table_viz <- table_viz %>%
        cols_align(align = "center", columns = all_of(center_cols))
    }
    if (length(left_cols) > 0) {
      table_viz <- table_viz %>%
        cols_align(align = "left", columns = all_of(left_cols))
    }
    if (length(right_cols) > 0) {
      table_viz <- table_viz %>%
        cols_align(align = "right", columns = all_of(right_cols))
    }
  }

  table_viz <- table_viz %>%
    cols_hide(
      columns = c(season, analysis_week)  # Hide columns
    )

  # Save as PNG with rate limiting
  sleep_time <- runif(1, min = 1, max = 3)
  cat("Waiting", round(sleep_time, 2), "seconds before saving PNG...\n")
  Sys.sleep(sleep_time)

  # Save the table as PNG
  gtsave(table_viz, png_file, expand = 10)
  cat("PNG table saved to:", png_file, "\n")
} else {
  cat("No sleepers found - skipping PNG table generation\n")
}

# Print summary
cat("\n=== Second-Year Sleepers for", year, "Season - Week", week,
    "(RB/WR/TE only) ===\n")
cat("Total second-year sleepers identified:", nrow(final_sleepers), "\n")
cat("[DEBUG] Script completed at:",
    format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n\n")

if (nrow(final_sleepers) > 0) {
  cat("Top 10 Sleepers (by sleeper score):\n")
  if (!exclude_current_fp) {
    display_cols <- c("sleeper_rank", "player", "position", "team", "opponent",
                      "ppg_prev", "prev_week_fp", "current_week_fp", "fp_delta",
                      "hit_status", "sleeper_score", "ecr")
    display_cols <- display_cols[display_cols %in% names(final_sleepers)]
    print(final_sleepers %>%
            head(10) %>%
            select(all_of(display_cols)))
  } else {
    display_cols <- c("sleeper_rank", "player", "position", "team", "opponent",
                      "ppg_prev", "avg_snap_pct_y1", "avg_snap_pct_y2",
                      "snap_pct_change", "sleeper_score", "ecr")
    display_cols <- display_cols[display_cols %in% names(final_sleepers)]

    # Add weekly FP columns if they exist
    fp_weekly_cols <- grep("^w[0-9]+$", names(final_sleepers), value = TRUE)
    if (length(fp_weekly_cols) > 0) {
      # Show just the first few weekly columns to keep output manageable
      display_cols <- c(display_cols[1:5], head(fp_weekly_cols, 3),
                        tail(display_cols, 2))
    }

    print(final_sleepers %>%
            head(10) %>%
            select(all_of(display_cols)))
  }

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