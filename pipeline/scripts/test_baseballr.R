# Test script to verify baseballr functions work correctly
# Run this to debug the "Invalid arguments or no daily batter data available!" error

source("config/setup.R")

cat("=== Testing baseballr functions ===\n")

# Test 1: Try different date ranges
test_dates <- c("2024-04-07", "2024-04-15", "2024-05-01", "2024-06-01")

for (test_date in test_dates) {
  cat(sprintf("\n--- Testing with date: %s ---\n", test_date))
  
  # Test bref_daily_batter
  tryCatch({
    cat("Testing bref_daily_batter...\n")
    batter_data <- bref_daily_batter(t1 = "2024-03-28", t2 = test_date)
    cat(sprintf("Success: Retrieved %d batter records\n", nrow(batter_data)))
    
    if (nrow(batter_data) > 0) {
      cat("Columns:", paste(names(batter_data), collapse = ", "), "\n")
      cat("Sample teams:", paste(head(unique(batter_data$Tm), 5), collapse = ", "), "\n")
    }
    
  }, error = function(e) {
    cat(sprintf("Error in bref_daily_batter: %s\n", e$message))
  })
  
  # Test bref_daily_pitcher
  tryCatch({
    cat("Testing bref_daily_pitcher...\n")
    pitcher_data <- bref_daily_pitcher(t1 = "2024-03-28", t2 = test_date)
    cat(sprintf("Success: Retrieved %d pitcher records\n", nrow(pitcher_data)))
    
    if (nrow(pitcher_data) > 0) {
      cat("Columns:", paste(names(pitcher_data), collapse = ", "), "\n")
      cat("Sample teams:", paste(head(unique(pitcher_data$Tm), 5), collapse = ", "), "\n")
    }
    
  }, error = function(e) {
    cat(sprintf("Error in bref_daily_pitcher: %s\n", e$message))
  })
}

# Test 2: Try alternative baseballr functions
cat("\n=== Testing alternative baseballr functions ===\n")

tryCatch({
  cat("Testing mlb_stats for team batting...\n")
  team_batting <- mlb_stats(stat_type = "season", stat_group = "hitting", season = 2024)
  cat(sprintf("Retrieved %d team batting records\n", nrow(team_batting)))
}, error = function(e) {
  cat(sprintf("Error: %s\n", e$message))
})

tryCatch({
  cat("Testing mlb_stats for team pitching...\n")
  team_pitching <- mlb_stats(stat_type = "season", stat_group = "pitching", season = 2024)
  cat(sprintf("Retrieved %d team pitching records\n", nrow(team_pitching)))
}, error = function(e) {
  cat(sprintf("Error: %s\n", e$message))
})

cat("\n=== Testing complete ===\n")