#!/usr/bin/env Rscript

# Load required libraries
suppressPackageStartupMessages({
  library(nflfastR)
  library(dplyr)
  library(tidyr)
  library(readr)
  library(nflreadr)
  library(ffpros)
})

# Parse command line arguments
args <- commandArgs(trailingOnly = TRUE)

# Check if correct number of arguments provided
if (length(args) != 2) {
  cat("Usage: Rscript collect_second_year_data.R [year] [output_directory]\n")
  cat("Example: Rscript collect_second_year_data.R 2024 ./data/\n")
  quit(status = 1)
}

year <- as.integer(args[1])
output_dir <- args[2]

# Validate year
current_year <- as.integer(format(Sys.Date(), "%Y"))
if (year < 2020 || year > current_year) {
  cat("Error: Year must be between 2020 and", current_year, "\n")
  quit(status = 1)
}

# Create output directory if it doesn't exist
if (!dir.exists(output_dir)) {
  dir.create(output_dir, recursive = TRUE)
  cat("Created output directory:", output_dir, "\n")
}

# Function to clean names for matching
clean_name <- function(name) {
  gsub("[^A-Za-z0-9 ]", "", tolower(trimws(name)))
}

# Function to safely add sleep time
safe_sleep <- function(min_time = 1, max_time = 3) {
  sleep_time <- runif(1, min = min_time, max = max_time)
  cat("Waiting", round(sleep_time, 2), "seconds to avoid rate limiting...\n")
  Sys.sleep(sleep_time)
}

# Load roster data once for the season
cat("Loading NFL roster data for", year, "season...\n")
safe_sleep()
rosters <- fast_scraper_roster(year)
cat("Loaded", nrow(rosters), "roster entries\n")

# Get second-year players (excluding QBs)
second_year_rosters <- rosters %>%
  filter(
    position %in% c("RB", "WR", "TE"),
    entry_year == (year - 1)
  ) %>%
  mutate(
    age = if_else(!is.na(birth_date),
                  as.integer(difftime(Sys.Date(), as.Date(birth_date), units = "days") / 365.25),
                  as.integer(22 + years_exp)),
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

cat("Found", nrow(second_year_rosters), "second-year players (RB/WR/TE only)\n")

# Load previous year stats
cat("Loading previous season stats...\n")
safe_sleep()
prev_year_stats <- load_player_stats(year - 1) %>%
  filter(season_type == "REG") %>%
  group_by(player_id, player_display_name, position) %>%
  summarise(
    games_y1 = n_distinct(week),
    total_fantasy_points_y1 = sum(fantasy_points_ppr, na.rm = TRUE),
    ppg_y1 = round(total_fantasy_points_y1 / games_y1, 2),
    .groups = "drop"
  )

# Load previous year snap counts
cat("Loading previous season snap counts...\n")
safe_sleep()
prev_year_snaps <- load_snap_counts(year - 1) %>%
  filter(game_type == "REG") %>%
  group_by(pfr_player_id, player, position, team) %>%
  summarise(
    total_games_y1_snaps = n_distinct(week),
    total_off_snaps_y1 = sum(offense_snaps, na.rm = TRUE),
    avg_snap_pct_y1 = round(mean(offense_pct, na.rm = TRUE), 1),
    .groups = "drop"
  ) %>%
  mutate(clean_snap_name = clean_name(player))

# Load roster and injury data for availability checks
cat("Loading roster and injury data for", year, "season...\n")
safe_sleep()
rosters_data <- tryCatch({
  load_rosters(seasons = year)
}, error = function(e) {
  cat("Warning: Could not load roster data:", e$message, "\n")
  NULL
})

injuries_data <- tryCatch({
  load_injuries(seasons = year)
}, error = function(e) {
  cat("Warning: Could not load injury data:", e$message, "\n")
  NULL
})

# Create active player lookup
active_players <- NULL
if (!is.null(rosters_data)) {
  active_players <- unique(rosters_data$full_name)
  active_players <- active_players[!is.na(active_players)]
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
  if (!is.null(injury_data) && nrow(injury_data) > 0 &&
      "full_name" %in% names(injury_data)) {
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

# Load ECR data once (single snapshot, not per-week)
cat("Fetching ECR data from FantasyPros (single snapshot)...\n")
safe_sleep()

ecr_data <- tryCatch({
  fp_rankings(page = 'consensus-cheatsheets', year = year, scoring = 'HALF') %>%
    filter(
      ecr > 100,  # Only include players ranked worse than 100
      pos %in% c("RB", "WR", "TE")
    ) %>%
    arrange(ecr) %>%  # Sort by ECR (best to worst)
    head(200) %>%     # Take first 200 players
    select(player = player_name, position = pos, team, ecr) %>%
    mutate(
      position = case_when(
        position == "RB" ~ "RB",
        position == "WR" ~ "WR",
        position == "TE" ~ "TE",
        TRUE ~ position
      ),
      clean_name = clean_name(player)
    )
}, error = function(e) {
  cat("Error: Could not fetch ECR data:", e$message, "\n")
  quit(status = 1)
})

cat("Loaded", nrow(ecr_data), "players from ECR snapshot\n")

# Loop through weeks 3-18
for (week in 3:18) {
  cat("\n=== Processing Week", week, "===\n")

  tryCatch({

    # Load current year snap counts up to this week
    cat("Loading snap count data through week", week, "...\n")
    safe_sleep()

    year2_weekly_snaps <- load_snap_counts(year) %>%
      filter(game_type == "REG", week <= !!week) %>%
      arrange(pfr_player_id, week)

    # Calculate week-to-week changes
    year2_weekly_snaps <- year2_weekly_snaps %>%
      group_by(pfr_player_id, player, position, team) %>%
      mutate(
        snap_pct_lag = lag(offense_pct),
        weekly_snap_change = offense_pct - snap_pct_lag
      ) %>%
      ungroup()

    # Calculate sliding window metrics if applicable
    sliding_window_snaps <- NULL
    if (week >= 3) {
      window_weeks <- c(week - 2, week - 1, week)

      sliding_window_snaps <- year2_weekly_snaps %>%
        filter(week %in% window_weeks) %>%
        arrange(pfr_player_id, week) %>%
        group_by(pfr_player_id, player, position, team) %>%
        mutate(
          snap_pct_prev = lag(offense_pct),
          window_snap_delta = offense_pct - snap_pct_prev
        ) %>%
        summarise(
          sliding_window_avg_delta = round(mean(window_snap_delta, na.rm = TRUE), 2),
          window_weeks_played = sum(!is.na(window_snap_delta)),
          .groups = "drop"
        ) %>%
        filter(window_weeks_played > 0) %>%
        mutate(clean_snap_name = clean_name(player))
    }

    # Aggregate current year snap stats
    current_year_snaps <- year2_weekly_snaps %>%
      group_by(pfr_player_id, player, position, team) %>%
      summarise(
        total_games_y2 = n_distinct(week),
        total_off_snaps_y2 = sum(offense_snaps, na.rm = TRUE),
        avg_snap_pct_y2 = round(mean(offense_pct, na.rm = TRUE), 1),
        w1_snap_share = first(offense_pct[week == min(week)], default = NA),
        y2_snap_share_change = round(mean(weekly_snap_change, na.rm = TRUE), 2),
        max_snap_pct_y2 = max(offense_pct, na.rm = TRUE),
        min_snap_pct_y2 = min(offense_pct, na.rm = TRUE),
        .groups = "drop"
      ) %>%
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

    # Load fantasy points data through previous week
    cat("Loading fantasy points data through week", week - 1, "...\n")
    safe_sleep()

    year2_fantasy_points <- load_player_stats(year) %>%
      filter(season_type == "REG", week < !!week) %>%
      select(player_id, player_display_name, position, week, fantasy_points_ppr) %>%
      group_by(player_id, player_display_name, position) %>%
      summarise(
        games_played_y2 = n(),
        total_fp_y2 = sum(fantasy_points_ppr, na.rm = TRUE),
        avg_fp_y2 = round(total_fp_y2 / games_played_y2, 1),
        max_fp_y2 = max(fantasy_points_ppr, na.rm = TRUE),
        min_fp_y2 = min(fantasy_points_ppr, na.rm = TRUE),
        .groups = "drop"
      ) %>%
      mutate(clean_fp_name = clean_name(player_display_name))

    # Get most recent 2 weeks of fantasy points
    recent_fp <- load_player_stats(year) %>%
      filter(season_type == "REG", week %in% c(max(1, week - 2), max(1, week - 1))) %>%
      select(player_id, week, fantasy_points_ppr) %>%
      pivot_wider(
        id_cols = player_id,
        names_from = week,
        values_from = fantasy_points_ppr,
        names_prefix = "week_"
      )

    # Get previous week fantasy points
    prev_week_fp <- load_player_stats(year) %>%
      filter(season_type == "REG", week == max(1, !!week - 1)) %>%
      select(player_id, prev_week_fp = fantasy_points_ppr)

    # Load schedule and opponent data
    cat("Loading schedule for week", week, "...\n")
    safe_sleep()

    weekly_schedule <- tryCatch({
      fast_scraper_schedules(year) %>%
        filter(week == !!week) %>%
        select(week, game_id, home_team, away_team)
    }, error = function(e) {
      cat("Warning: Could not load schedule data for week", week, "\n")
      NULL
    })

    opponent_mapping <- NULL
    if (!is.null(weekly_schedule)) {
      home_opponents <- weekly_schedule %>%
        select(team = home_team, opponent = away_team, week)

      away_opponents <- weekly_schedule %>%
        select(team = away_team, opponent = home_team, week)

      opponent_mapping <- bind_rows(home_opponents, away_opponents) %>%
        distinct()
    }

    # Match second-year players with ECR data (inner join to only include players with ECR > 100)
    ml_data <- second_year_rosters %>%
      inner_join(
        ecr_data %>% select(clean_name, position, ecr),
        by = c("clean_name", "position")
      )

    # Join with previous year stats
    ml_data <- ml_data %>%
      left_join(
        prev_year_stats %>% select(player_id, games_y1, total_fantasy_points_y1, ppg_y1),
        by = "player_id"
      ) %>%
      mutate(across(c(games_y1, total_fantasy_points_y1, ppg_y1), ~ coalesce(.x, 0)))

    # Join with previous year snap counts
    ml_data <- ml_data %>%
      left_join(
        prev_year_snaps %>% select(clean_snap_name, position, avg_snap_pct_y1, total_off_snaps_y1),
        by = c("clean_name" = "clean_snap_name", "position")
      ) %>%
      mutate(across(c(avg_snap_pct_y1, total_off_snaps_y1), ~ coalesce(.x, 0)))

    # Join with current year snap counts
    ml_data <- ml_data %>%
      left_join(
        current_year_snaps %>% select(-position, -team, -player),
        by = c("clean_name" = "clean_snap_name")
      ) %>%
      mutate(across(c(total_games_y2, total_off_snaps_y2, avg_snap_pct_y2, w1_snap_share,
                     y2_snap_share_change, sliding_window_avg_delta, max_snap_pct_y2, min_snap_pct_y2),
                   ~ coalesce(.x, 0)))

    # Join with fantasy points data
    ml_data <- ml_data %>%
      left_join(
        year2_fantasy_points %>% select(-position, -player_display_name, -clean_fp_name),
        by = c("player_id" = "player_id")
      ) %>%
      mutate(across(c(games_played_y2, total_fp_y2, avg_fp_y2, max_fp_y2, min_fp_y2),
                   ~ coalesce(.x, 0)))

    # Join recent fantasy points
    ml_data <- ml_data %>%
      left_join(recent_fp, by = "player_id") %>%
      left_join(prev_week_fp, by = "player_id")

    # Add opponent data if available
    if (!is.null(opponent_mapping)) {
      ml_data <- ml_data %>%
        left_join(
          opponent_mapping %>% select(team, opponent),
          by = "team"
        ) %>%
        mutate(opponent = coalesce(opponent, "BYE"))
    } else {
      ml_data <- ml_data %>%
        mutate(opponent = "N/A")
    }

    # Check player availability
    cat("Checking player availability for", nrow(ml_data), "players...\n")
    player_availability <- vector("logical", nrow(ml_data))
    for (i in seq_len(nrow(ml_data))) {
      tryCatch({
        result <- check_player_status(
          ml_data$player[i],
          week,
          active_players,
          injuries_data
        )
        player_availability[i] <- result$available
      }, error = function(e) {
        cat("Error checking availability for player", ml_data$player[i],
            ":", e$message, "\n")
        player_availability[i] <- TRUE
      })
    }
    ml_data$player_available <- player_availability
    cat("Completed availability check\n")

    # Filter by player availability
    initial_count <- nrow(ml_data)
    ml_data <- ml_data %>%
      filter(player_available)
    filtered_count <- nrow(ml_data)
    cat("[DEBUG] Player status filtering: Started with", initial_count,
        "candidates\n")
    cat("[DEBUG] After filtering out injured/unavailable players:",
        filtered_count, "candidates remain\n")
    cat("[DEBUG] Excluded", initial_count - filtered_count,
        "players due to roster/injury status in week", week, "\n")

    # Filter for meaningful snap counts in recent weeks
    if (week >= 2) {
      initial_count <- nrow(ml_data)

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
        filter(max_recent_snap_pct >= 10 |
               (recent_snaps >= 5 & recent_weeks_played >= 1)) %>%
        mutate(clean_snap_name = clean_name(player))

      if (nrow(recent_snap_players) > 0 &&
          !is.null(recent_snap_players$clean_snap_name)) {
        valid_snap_names <- as.character(
          recent_snap_players$clean_snap_name[
            !is.na(recent_snap_players$clean_snap_name)])

        if (length(valid_snap_names) > 0) {
          clean_names_vec <- as.character(ml_data$clean_name)
          matching_indices <- clean_names_vec %in% valid_snap_names
          ml_data <- ml_data[matching_indices, ]
        }
      }

      filtered_count <- nrow(ml_data)
      cat("[DEBUG] Snap count filtering: Started with", initial_count,
          "candidates\n")
      cat("[DEBUG] After snap count filtering:", filtered_count,
          "candidates remain\n")
      cat("[DEBUG] Excluded", initial_count - filtered_count,
          "players with minimal recent snap counts\n")
    }

    # Calculate derived features
    cat("Calculating derived features...\n")
    cat("Data dimensions before mutate:", nrow(ml_data), "rows,", ncol(ml_data), "cols\n")

    ml_data <- tryCatch({
      ml_data %>%
        mutate(
          # Snap percentage changes
          snap_pct_change = avg_snap_pct_y2 - avg_snap_pct_y1,
          snap_pct_variance = if_else(!is.na(avg_snap_pct_y2) & avg_snap_pct_y2 > 0,
                                      round(abs(w1_snap_share - avg_snap_pct_y2), 2), 0),

        # Efficiency metrics
        fp_per_snap_y1 = if_else(!is.na(total_off_snaps_y1) & total_off_snaps_y1 > 0,
                                 round(total_fantasy_points_y1 / total_off_snaps_y1, 3), 0),
        fp_per_snap_y2 = if_else(!is.na(total_off_snaps_y2) & total_off_snaps_y2 > 0,
                                 round(total_fp_y2 / total_off_snaps_y2, 3), 0),

        # Binary indicators
        crossed_10pct_snaps = as.integer(!is.na(avg_snap_pct_y2) & avg_snap_pct_y2 >= 10),
        crossed_20pct_snaps = as.integer(!is.na(avg_snap_pct_y2) & avg_snap_pct_y2 >= 20),
        crossed_30pct_snaps = as.integer(!is.na(avg_snap_pct_y2) & avg_snap_pct_y2 >= 30),
        has_positive_trend = as.integer(!is.na(sliding_window_avg_delta) & sliding_window_avg_delta > 0),
        significant_snap_jump = as.integer(!is.na(snap_pct_change) & snap_pct_change >= 10),

        # Draft capital
        is_udfa = as.integer(is.na(draft_number)),
        is_day3_pick = as.integer(!is.na(draft_number) & draft_number > 100),
        is_early_pick = as.integer(!is.na(draft_number) & draft_number <= 50),

        # Physical profile (position-specific)
        rb_size_score = if_else(!is.na(position) & position == "RB" & !is.na(weight),
                                round((weight - 200) / 20, 2), 0),
        wr_height_score = if_else(!is.na(position) & position == "WR" & !is.na(height),
                                  round((height - 70) / 5, 2), 0),
        te_size_score = if_else(!is.na(position) & position == "TE" & !is.na(weight),
                                round((weight - 240) / 20, 2), 0),

        # Age factors
        is_young_breakout = as.integer(!is.na(age) & age <= 23),

        # Usage metrics
        rookie_year_usage = if_else(!is.na(total_off_snaps_y1) & total_off_snaps_y1 > 0,
                                    round(total_off_snaps_y1 / (17 * 60), 2), 0),

        # Consistency metrics
        fp_consistency_y2 = if_else(!is.na(avg_fp_y2) & !is.na(max_fp_y2) & avg_fp_y2 > 0 & max_fp_y2 > 0,
                                    round(min_fp_y2 / max_fp_y2, 2), 0),
        snap_consistency_y2 = if_else(!is.na(avg_snap_pct_y2) & !is.na(max_snap_pct_y2) & avg_snap_pct_y2 > 0 & max_snap_pct_y2 > 0,
                                      round(min_snap_pct_y2 / max_snap_pct_y2, 2), 0),

        # Meta data
        season = year,
        analysis_week = week
        )
    }, error = function(e) {
      cat("ERROR in mutate():", e$message, "\n")
      cat("Problematic columns:\n")
      cat("  avg_snap_pct_y2 has NAs:", sum(is.na(ml_data$avg_snap_pct_y2)), "\n")
      cat("  avg_snap_pct_y1 has NAs:", sum(is.na(ml_data$avg_snap_pct_y1)), "\n")
      cat("  w1_snap_share has NAs:", sum(is.na(ml_data$w1_snap_share)), "\n")
      cat("  total_off_snaps_y1 has NAs:", sum(is.na(ml_data$total_off_snaps_y1)), "\n")
      cat("  total_off_snaps_y2 has NAs:", sum(is.na(ml_data$total_off_snaps_y2)), "\n")
      cat("  max_snap_pct_y2 has NAs:", sum(is.na(ml_data$max_snap_pct_y2)), "\n")
      cat("  age has NAs:", sum(is.na(ml_data$age)), "\n")
      stop(e)  # Re-throw the error
    })

    cat("Completed derived features calculation\n")

    # Select final columns for output
    cat("Selecting final columns...\n")
    cat("Available columns:", paste(names(ml_data), collapse=", "), "\n")

    final_data <- tryCatch({
      ml_data %>%
        select(
        # Identifiers
        player_id, player, position, team, opponent,

        # Draft and physical
        draft_number, college, height, weight, age, entry_year, years_exp,

        # Year 1 stats
        games_y1, total_fantasy_points_y1, ppg_y1, total_off_snaps_y1, avg_snap_pct_y1,
        fp_per_snap_y1,

        # Year 2 stats
        total_games_y2, total_off_snaps_y2, avg_snap_pct_y2, w1_snap_share,
        y2_snap_share_change, sliding_window_avg_delta, max_snap_pct_y2, min_snap_pct_y2,
        games_played_y2, total_fp_y2, avg_fp_y2, max_fp_y2, min_fp_y2,
        fp_per_snap_y2,

        # Recent weeks FP (if available)
        prev_week_fp, starts_with("week_"),

        # Derived features
        snap_pct_change, snap_pct_variance, fp_consistency_y2, snap_consistency_y2,
        crossed_10pct_snaps, crossed_20pct_snaps, crossed_30pct_snaps,
        has_positive_trend, significant_snap_jump,
        is_udfa, is_day3_pick, is_early_pick, is_young_breakout,
        rb_size_score, wr_height_score, te_size_score,
        rookie_year_usage,

        # ECR and availability
        ecr, player_available,

        # Meta
        season, analysis_week
        ) %>%
        # Remove clean_name helper column
        select(-any_of("clean_name"))
    }, error = function(e) {
      cat("ERROR in select():", e$message, "\n")
      cat("Available column names:\n")
      print(names(ml_data))
      stop(e)  # Re-throw the error
    })

    cat("Selected", ncol(final_data), "columns\n")

    # Filter out players with >10 fantasy points in previous week
    if (week > 1) {
      initial_count <- nrow(final_data)
      final_data <- final_data %>%
        filter(is.na(prev_week_fp) | prev_week_fp <= 10)
      filtered_count <- nrow(final_data)
      cat("[DEBUG] Filtered out", initial_count - filtered_count,
          "players with >10 FP in previous week\n")
      cat("[DEBUG] Remaining candidates:", filtered_count, "\n")
    }

    # Generate output filename
    output_file <- file.path(output_dir, paste0("second_year_", year,
                                                  "_week", week, ".csv"))

    # Save to CSV
    cat("Writing to file:", output_file, "\n")
    write_csv(final_data, output_file)
    cat("Saved", nrow(final_data), "records to:", output_file, "\n")

  }, error = function(e) {
    cat("Error processing week", week, ":", e$message, "\n")
    cat("Continuing to next week...\n")
  })
}

cat("\n=== Data collection complete ===\n")
cat("Output directory:", output_dir, "\n")
cat("Files created for weeks 3-18 of", year, "season\n")