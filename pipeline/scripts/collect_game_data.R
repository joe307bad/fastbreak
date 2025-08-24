# Main script for collecting baseball game data
# Creates CSV files with comprehensive game statistics including:
# - Starting pitchers and their performance metrics
# - Team advanced statistics from FanGraphs (wOBA, ERA-, FIP)
# - Game outcomes and scores

# Load configuration and setup
source("config/setup.R")
source("config/config.R")
source("utils/data_utils.R")

# Logging configuration
LOG_LEVEL <- Sys.getenv("LOG_LEVEL", "INFO")  # DEBUG, INFO, WARN, ERROR
VERBOSE <- LOG_LEVEL %in% c("DEBUG", "INFO")

log_message <- function(level, message) {
  if (level == "DEBUG" && LOG_LEVEL != "DEBUG") return()
  if (level == "INFO" && !VERBOSE) return()
  
  timestamp <- format(Sys.time(), "%Y-%m-%d %H:%M:%S")
  cat(sprintf("[%s] %s: %s\n", timestamp, level, message))
}

#' Collect comprehensive game data for date range
#' @param start_date Start date (YYYY-MM-DD format)
#' @param end_date End date (YYYY-MM-DD format) 
#' @param output_file Output CSV filename
collect_game_data <- function(start_date = DEFAULT_START_DATE, 
                             end_date = DEFAULT_END_DATE,
                             output_file = OUTPUT_FILENAME) {
  
  log_message("INFO", sprintf("Starting data collection from %s to %s", start_date, end_date))
  
  # Validate inputs
  if (is.na(as.Date(start_date)) || is.na(as.Date(end_date))) {
    stop("Invalid date format. Use YYYY-MM-DD")
  }
  
  # Step 1: Get game schedule
  log_message("INFO", "Fetching game schedule...")
  games <- tryCatch({
    get_game_schedule(start_date, end_date)
  }, error = function(e) {
    log_message("ERROR", sprintf("Failed to fetch game schedule: %s", e$message))
    stop(e)
  })
  
  if (nrow(games) == 0) {
    log_message("WARN", "No games found for the specified date range")
    return(data.frame())
  }
  
  log_message("INFO", sprintf("Found %d games", nrow(games)))
  
  # Step 2: Process each game
  if (Sys.getenv("LIMIT_GAMES", "FALSE") == "TRUE") {
    games <- games[1, , drop = FALSE]
    cat("Limited to single game for testing\n")
  }
  
  game_data_list <- list()
  
  for (i in 1:nrow(games)) {
    game <- games[i, ]
    cat(sprintf("Game %d/%d: %s @ %s (%s)\n", 
                i, nrow(games), 
                game$teams_away_team_name, 
                game$teams_home_team_name,
                game$official_date))
    
    # Get starting pitchers
    pitchers <- get_starting_pitchers(game$game_pk, game$official_date)
    
    tryCatch({
      # Get pitcher stats
      home_pitcher_stats <- get_pitcher_stats(pitchers$home_pitcher, game$official_date)
      away_pitcher_stats <- get_pitcher_stats(pitchers$away_pitcher, game$official_date)
      
      # Get team stats
      home_team_stats <- get_team_stats(game$teams_home_team_name, game$official_date)
      away_team_stats <- get_team_stats(game$teams_away_team_name, game$official_date)
      
      # Display comprehensive column-value format
      cat("  home_pitcher:", pitchers$home_pitcher %||% "null", "\n")
      cat("  away_pitcher:", pitchers$away_pitcher %||% "null", "\n")
      cat("  home_pitcher_era:", home_pitcher_stats$era %||% "null", "\n")
      cat("  away_pitcher_era:", away_pitcher_stats$era %||% "null", "\n")
      cat("  home_pitcher_whip:", home_pitcher_stats$whip %||% "null", "\n")
      cat("  away_pitcher_whip:", away_pitcher_stats$whip %||% "null", "\n")
      cat("  home_pitcher_k:", home_pitcher_stats$k %||% "null", "\n")
      cat("  away_pitcher_k:", away_pitcher_stats$k %||% "null", "\n")
      cat("  home_pitcher_bb:", home_pitcher_stats$bb %||% "null", "\n")
      cat("  away_pitcher_bb:", away_pitcher_stats$bb %||% "null", "\n")
      cat("  home_pitcher_ip:", home_pitcher_stats$ip %||% "null", "\n")
      cat("  away_pitcher_ip:", away_pitcher_stats$ip %||% "null", "\n")
      cat("  home_team_ops:", home_team_stats$ops %||% "null", "\n")
      cat("  away_team_ops:", away_team_stats$ops %||% "null", "\n")
      cat("  home_team_woba:", home_team_stats$woba %||% "null", "\n")
      cat("  away_team_woba:", away_team_stats$woba %||% "null", "\n")
      cat("  home_team_era_minus:", home_team_stats$era_plus %||% "null", "\n")
      cat("  away_team_era_minus:", away_team_stats$era_plus %||% "null", "\n")
      cat("  home_team_fip:", home_team_stats$fip %||% "null", "\n")
      cat("  away_team_fip:", away_team_stats$fip %||% "null", "\n")
      cat("  home_score:", game$teams_home_score %||% "null", "\n")
      cat("  away_score:", game$teams_away_score %||% "null", "\n")
      cat("  status: success\n\n")
      
      # Add processed data to list
      game_data_list[[i]] <- game %>%
        mutate(
          pitchers = list(pitchers),
          home_pitcher_stats = list(home_pitcher_stats),
          away_pitcher_stats = list(away_pitcher_stats),
          home_team_stats = list(home_team_stats),
          away_team_stats = list(away_team_stats)
        )
      
    }, error = function(e) {
      # Display error with all fields as null
      cat("  home_pitcher:", pitchers$home_pitcher %||% "null", "\n")
      cat("  away_pitcher:", pitchers$away_pitcher %||% "null", "\n")
      cat("  home_pitcher_era: null\n")
      cat("  away_pitcher_era: null\n")
      cat("  home_pitcher_whip: null\n")
      cat("  away_pitcher_whip: null\n")
      cat("  home_pitcher_k: null\n")
      cat("  away_pitcher_k: null\n")
      cat("  home_pitcher_bb: null\n")
      cat("  away_pitcher_bb: null\n")
      cat("  home_pitcher_ip: null\n")
      cat("  away_pitcher_ip: null\n")
      cat("  home_team_ops: null\n")
      cat("  away_team_ops: null\n")
      cat("  home_team_woba: null\n")
      cat("  away_team_woba: null\n")
      cat("  home_team_era_minus: null\n")
      cat("  away_team_era_minus: null\n")
      cat("  home_team_fip: null\n")
      cat("  away_team_fip: null\n")
      cat("  home_score:", game$teams_home_score %||% "null", "\n")
      cat("  away_score:", game$teams_away_score %||% "null", "\n")
      cat("  status: error -", e$message, "\n\n")
      
      # Add game with NA stats to maintain structure
      game_data_list[[i]] <- game %>%
        mutate(
          pitchers = list(pitchers),
          home_pitcher_stats = list(list(name = NA, era = NA, whip = NA, k = NA, bb = NA, ip = NA)),
          away_pitcher_stats = list(list(name = NA, era = NA, whip = NA, k = NA, bb = NA, ip = NA)),
          home_team_stats = list(list(ops = NA, woba = NA, era_plus = NA, fip = NA)),
          away_team_stats = list(list(ops = NA, woba = NA, era_plus = NA, fip = NA))
        )
    })
  }
  
  # Combine and save results
  game_data <- bind_rows(game_data_list)
  output_data <- format_output_data(game_data)
  
  output_path <- file.path(OUTPUT_DIR, output_file)
  if (!dir.exists(OUTPUT_DIR)) {
    dir.create(OUTPUT_DIR, recursive = TRUE)
  }
  
  write_csv(output_data, output_path)
  cat("Data written to", output_path, "\n")
  
  return(output_data)
}

#' Get game schedule for date range
get_game_schedule <- function(start_date, end_date) {
  # Convert dates
  start_dt <- as.Date(start_date)
  end_dt <- as.Date(end_date)
  
  # Get schedule from baseballr
  schedule <- mlb_schedule(
    season = year(start_dt),
    level_ids = 1  # MLB level
  ) %>%
    filter(
      # Filter by date range
      as.Date(official_date) >= start_dt,
      as.Date(official_date) <= end_dt,
      # Only completed games
      status_abstract_game_state == "Final",
      !is.na(teams_away_score),
      !is.na(teams_home_score)
    )
  
  return(schedule)
}

# Example usage (uncomment to run)
# game_data <- collect_game_data("2024-04-01", "2024-04-07")
# head(game_data)