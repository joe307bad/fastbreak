# Main script for collecting baseball game data
# Creates CSV files with comprehensive game statistics including:
# - Starting pitchers and their performance metrics
# - Team advanced statistics from FanGraphs (wOBA, ERA-, FIP)
# - Weather conditions
# - Game outcomes and scores

# Load configuration and setup
source("config/setup.R")
source("config/config.R")
source("utils/data_utils.R")
source("utils/weather_utils.R")

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
  cat("Processing game data...\n")
  
  # Option to limit to single game for testing (set LIMIT_GAMES environment variable)
  if (Sys.getenv("LIMIT_GAMES", "FALSE") == "TRUE") {
    games <- games[1, , drop = FALSE]
    log_message("INFO", "Limited to single game for testing (LIMIT_GAMES=TRUE)")
  }
  
  # Process games one by one with detailed logging
  game_data_list <- list()
  
  for (i in 1:nrow(games)) {
    game <- games[i, ]
    cat(sprintf("Processing game %d/%d: %s vs %s on %s (GamePK: %s)\n", 
                i, nrow(games), 
                game$teams_away_team_name, 
                game$teams_home_team_name,
                game$official_date,
                game$game_pk))
    
    # Get starting pitchers - fail immediately if not found
    cat("  - Getting starting pitchers...\n")
    pitchers <- get_starting_pitchers(game$game_pk, game$official_date)
    cat(sprintf("    Home: %s, Away: %s\n", pitchers$home_pitcher, pitchers$away_pitcher))
    
    tryCatch({
      # Get point-in-time pitcher stats
      cat("  - Getting pitcher stats...\n")
      home_pitcher_stats <- get_pitcher_stats(pitchers$home_pitcher, game$official_date)
      cat(sprintf("    Home pitcher stats retrieved: ERA=%s\n", 
                  ifelse(is.na(home_pitcher_stats$era), "NA", home_pitcher_stats$era)))
      
      away_pitcher_stats <- get_pitcher_stats(pitchers$away_pitcher, game$official_date)
      cat(sprintf("    Away pitcher stats retrieved: ERA=%s\n", 
                  ifelse(is.na(away_pitcher_stats$era), "NA", away_pitcher_stats$era)))
      
      # Get point-in-time team stats
      cat("  - Getting team stats...\n")
      home_team_stats <- get_team_stats(game$teams_home_team_name, game$official_date)
      cat(sprintf("    Home team stats: OPS=%s\n", 
                  ifelse(is.na(home_team_stats$ops), "NA", home_team_stats$ops)))
      
      away_team_stats <- get_team_stats(game$teams_away_team_name, game$official_date)
      cat(sprintf("    Away team stats: OPS=%s\n", 
                  ifelse(is.na(away_team_stats$ops), "NA", away_team_stats$ops)))
      
      # Get weather data
      cat("  - Getting weather data...\n")
      weather <- get_weather_data(game$teams_home_team_name, game$official_date)
      cat(sprintf("    Weather: Temp=%s°F, Wind=%s mph\n", 
                  weather$temperature, weather$wind_speed))
      
      # Add processed data to list
      game_data_list[[i]] <- game %>%
        mutate(
          pitchers = list(pitchers),
          home_pitcher_stats = list(home_pitcher_stats),
          away_pitcher_stats = list(away_pitcher_stats),
          home_team_stats = list(home_team_stats),
          away_team_stats = list(away_team_stats),
          weather = list(weather)
        )
      
      cat("  ✓ Game processed successfully\n\n")
      
    }, error = function(e) {
      cat(sprintf("  ✗ Error processing game: %s\n", e$message))
      
      # Add game with NA stats to maintain structure
      game_data_list[[i]] <- game %>%
        mutate(
          pitchers = list(pitchers),
          home_pitcher_stats = list(list(name = NA, era = NA, whip = NA, k = NA, bb = NA, ip = NA)),
          away_pitcher_stats = list(list(name = NA, era = NA, whip = NA, k = NA, bb = NA, ip = NA)),
          home_team_stats = list(list(ops = NA, woba = NA, era_plus = NA, fip = NA)),
          away_team_stats = list(list(ops = NA, woba = NA, era_plus = NA, fip = NA)),
          weather = list(list(temperature = NA, wind_speed = NA, wind_direction = NA, precipitation = NA))
        )
      cat("  - Continuing with NA values for this game\n\n")
    })
  }
  
  # Combine all processed games
  cat("Combining processed game data...\n")
  game_data <- bind_rows(game_data_list)
  
  # Step 3: Format output
  cat("Formatting output data...\n")
  output_data <- format_output_data(game_data)
  
  # Step 4: Write to CSV
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