# Baseball Game Data Collection Pipeline
# This script collects comprehensive game data including:
# - Starting pitchers and their statistics
# - Team batting and pitching statistics (wOBA, ERA-, FIP)
# - Weather data
# - Game outcomes
#
# Usage:
#   Rscript scripts/collect_daily_games.R [date] [output_file]
#   Rscript scripts/collect_daily_games.R --season [year] [output_file] [--batch-size N]
#
# Examples:
#   Rscript scripts/collect_daily_games.R 2024-04-01 game_data.csv
#   Rscript scripts/collect_daily_games.R --season 2024
#   Rscript scripts/collect_daily_games.R --season 2024 --batch-size 10
#
# Season mode processes games in batches (default 100) and creates files named:
# mlb_season_{year}_{start_date}_{end_date}_batch{number}.csv

# Load required libraries and functions
source("config/setup.R")
source("scripts/collect_game_data.R")

# Configuration
DEFAULT_DATE <- "2024-04-01"
DEFAULT_OUTPUT_FILE <- "game_data.csv"

# Process season data in batches of games
process_season_batched <- function(start_date, end_date, output_prefix, batch_size = 100) {
  cat("=====================================\n")
  
  tryCatch({
    # Load required functions
    source("scripts/collect_game_data.R")
    
    # Get all games for the season
    cat("Fetching season schedule...\n")
    games <- get_game_schedule(start_date, end_date)
    
    if (nrow(games) == 0) {
      cat("No games found for the specified season\n")
      return(data.frame())
    }
    
    total_games <- nrow(games)
    cat(sprintf("Found %d games in season\n", total_games))
    
    # Process in batches
    batch_count <- ceiling(total_games / batch_size)
    
    cat(sprintf("Processing %d batches of %d games each\n", batch_count, batch_size))
    
    all_data <- list()
    
    for (batch_num in 1:batch_count) {
      start_idx <- (batch_num - 1) * batch_size + 1
      end_idx <- min(batch_num * batch_size, total_games)
      
      batch_games <- games[start_idx:end_idx, ]
      batch_start_date <- min(as.Date(batch_games$official_date))
      batch_end_date <- max(as.Date(batch_games$official_date))
      
      cat(sprintf("\n=== Batch %d/%d ===\n", batch_num, batch_count))
      cat(sprintf("Games %d-%d (%s to %s)\n", start_idx, end_idx, batch_start_date, batch_end_date))
      
      # Generate batch filename
      batch_filename <- sprintf("%s_batch%03d.csv", output_prefix, batch_num)
      
      # Process this batch
      batch_data <- collect_game_data_batch(batch_games, batch_filename)
      
      if (!is.null(batch_data) && nrow(batch_data) > 0) {
        all_data[[batch_num]] <- batch_data
        cat(sprintf("Batch %d completed: %d games processed\n", batch_num, nrow(batch_data)))
      } else {
        cat(sprintf("Batch %d failed or returned no data\n", batch_num))
      }
    }
    
    # Summary
    total_processed <- sum(sapply(all_data, nrow))
    cat(sprintf("\n=== Season Collection Summary ===\n"))
    cat(sprintf("Total games processed: %d/%d\n", total_processed, total_games))
    cat(sprintf("Batches created: %d\n", length(all_data)))
    
    # Return combined data
    if (length(all_data) > 0) {
      combined_data <- bind_rows(all_data)
      return(combined_data)
    } else {
      return(data.frame())
    }
    
  }, error = function(e) {
    cat(sprintf("Error in season processing: %s\n", e$message))
    return(NULL)
  })
}

# Process a batch of games
collect_game_data_batch <- function(games, output_file) {
  # Set up output directory
  output_dir <- "output"
  if (!dir.exists(output_dir)) {
    dir.create(output_dir, recursive = TRUE)
  }
  
  # Use existing collect_game_data logic but with pre-filtered games
  tryCatch({
    # Process each game in the batch
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
        # Get pitcher and team stats
        home_pitcher_stats <- get_pitcher_stats(pitchers$home_pitcher, game$official_date)
        away_pitcher_stats <- get_pitcher_stats(pitchers$away_pitcher, game$official_date)
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
        
        # Add to list
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
        
        # Add game with NA stats
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
    
    # Combine and format data
    game_data <- bind_rows(game_data_list)
    output_data <- format_output_data(game_data)
    
    # Write to CSV
    output_path <- file.path(output_dir, output_file)
    write_csv(output_data, output_path)
    cat(sprintf("Batch data written to %s\n", output_path))
    
    return(output_data)
    
  }, error = function(e) {
    cat(sprintf("Error in batch processing: %s\n", e$message))
    return(NULL)
  })
}

# Parse command line arguments
args <- commandArgs(trailingOnly = TRUE)
season_mode <- FALSE
start_date <- DEFAULT_DATE
end_date <- DEFAULT_DATE
output_file <- DEFAULT_OUTPUT_FILE
batch_size <- 100  # Default batch size

if (length(args) > 0) {
  if (args[1] == "--season") {
    season_mode <- TRUE
    if (length(args) >= 2) {
      year <- as.numeric(args[2])
      if (!is.na(year) && year >= 1900 && year <= 2050) {
        # Set season dates (March 28 to September 30)
        start_date <- sprintf("%d-03-28", year)
        end_date <- sprintf("%d-09-30", year)
      } else {
        cat("Error: Invalid year provided. Using default dates.\n")
      }
    } else {
      cat("Error: --season requires a year argument. Using default dates.\n")
      season_mode <- FALSE
    }
    
    # Parse remaining arguments for output file and batch size
    remaining_args <- if(length(args) >= 3) args[3:length(args)] else c()
    
    # Check for --batch-size in remaining args
    batch_size_idx <- which(remaining_args == "--batch-size")
    if (length(batch_size_idx) > 0 && length(remaining_args) > batch_size_idx) {
      batch_size <- as.numeric(remaining_args[batch_size_idx + 1])
      if (is.na(batch_size) || batch_size < 1) {
        cat("Error: Invalid batch size. Using default of 100.\n")
        batch_size <- 100
      }
      # Remove batch size args from remaining args
      remaining_args <- remaining_args[-c(batch_size_idx, batch_size_idx + 1)]
    }
    
    # Set output file
    if (length(remaining_args) >= 1) {
      output_file <- remaining_args[1]
    } else {
      year <- if(season_mode) args[2] else format(Sys.Date(), "%Y")
      output_file <- sprintf("mlb_season_%s_%s_%s", year, gsub("-", "", start_date), gsub("-", "", end_date))
    }
  } else {
    # Single date mode
    start_date <- args[1]
    end_date <- args[1]
    
    if (length(args) >= 2) {
      output_file <- args[2]
    }
  }
}

# Main execution
main <- function(start_date = DEFAULT_DATE, end_date = DEFAULT_DATE, output_file = DEFAULT_OUTPUT_FILE, season_mode = FALSE, batch_size = 100) {
  cat("=== Baseball Game Data Collection ===\n")
  
  if (season_mode) {
    cat(sprintf("Season Mode: %s to %s (batch size: %d)\n", start_date, end_date, batch_size))
    return(process_season_batched(start_date, end_date, output_file, batch_size))
  } else {
    cat(sprintf("Date: %s\n", start_date))
  }
  
  cat(sprintf("Output: %s\n", output_file))
  cat("=====================================\n")
  
  tryCatch({
    # Collect game data
    game_data <- collect_game_data(
      start_date = start_date,
      end_date = end_date,
      output_file = output_file
    )
    
    # Summary
    cat("\n=== Collection Summary ===\n")
    cat(sprintf("Games processed: %d\n", nrow(game_data)))
    cat(sprintf("Output file: output/%s\n", output_file))
    
    if (nrow(game_data) > 0) {
      cat("\nSample game data:\n")
      print(game_data[1, c("GameId", "HomeTeam", "AwayTeam", "HomeScore", "AwayScore", 
                           "HomePitcherName", "AwayPitcherName", "HomeOPS", "AwayOPS")])
    }
    
    return(game_data)
    
  }, error = function(e) {
    cat(sprintf("Error: %s\n", e$message))
    return(NULL)
  })
}

# Run main function
result <- main(start_date = start_date, end_date = end_date, output_file = output_file, season_mode = season_mode, batch_size = batch_size)