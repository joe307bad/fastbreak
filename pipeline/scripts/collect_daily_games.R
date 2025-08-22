# Baseball Game Data Collection Pipeline
# This script collects comprehensive game data including:
# - Starting pitchers and their statistics
# - Team batting and pitching statistics (wOBA, ERA-, FIP)
# - Weather data
# - Game outcomes

# Load required libraries and functions
source("config/setup.R")
source("scripts/collect_game_data.R")

# Configuration
DEFAULT_DATE <- "2024-04-01"
DEFAULT_OUTPUT_FILE <- "game_data.csv"

# Main execution
main <- function(date = DEFAULT_DATE, output_file = DEFAULT_OUTPUT_FILE) {
  cat("=== Baseball Game Data Collection ===\n")
  cat(sprintf("Date: %s\n", date))
  cat(sprintf("Output: %s\n", output_file))
  cat("=====================================\n")
  
  tryCatch({
    # Collect game data
    game_data <- collect_game_data(
      start_date = date,
      end_date = date,
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
result <- main()