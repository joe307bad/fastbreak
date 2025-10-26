#!/usr/bin/env Rscript
# Check hoopR function parameters

library(hoopR)

# Check the help for load_nba_team_box
cat("load_nba_team_box function signature:\n")
print(args(load_nba_team_box))

# Try loading with seasons parameter
cat("\nTrying to load season 2025...\n")
tryCatch({
  team_box_2025 <- load_nba_team_box(seasons = 2025)
  cat("Success! Rows:", nrow(team_box_2025), "\n")
}, error = function(e) {
  cat("Error:", e$message, "\n")
})

# Try espn_nba_team_box instead
cat("\nTrying espn_nba_team_box...\n")
tryCatch({
  print(args(espn_nba_team_box))
}, error = function(e) {
  cat("Function not available\n")
})
