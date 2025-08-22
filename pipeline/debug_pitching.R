# Debug script to examine team names in pitching data
source("config/setup.R")

cat("=== Debugging Team Names in Pitching Data ===\n")

tryCatch({
  # Get pitching data
  pitching_data <- bref_daily_pitcher(
    t1 = "2024-03-28",
    t2 = "2024-04-01"
  )
  
  cat("Pitching data retrieved. Rows:", nrow(pitching_data), "\n")
  cat("Column names:\n")
  cat(paste(names(pitching_data), collapse = ", "), "\n\n")
  
  # Check for team column
  team_cols <- names(pitching_data)[grepl("team|tm", names(pitching_data), ignore.case = TRUE)]
  cat("Team-related columns:", paste(team_cols, collapse = ", "), "\n\n")
  
  if ("Tm" %in% names(pitching_data)) {
    cat("Unique teams in pitching data (Tm column):\n")
    unique_teams <- unique(pitching_data$Tm)
    cat(paste(unique_teams, collapse = ", "), "\n\n")
  }
  
  if ("Team" %in% names(pitching_data)) {
    cat("Unique teams in pitching data (Team column):\n")
    unique_teams <- unique(pitching_data$Team)
    cat(paste(unique_teams, collapse = ", "), "\n\n")
  }
  
}, error = function(e) {
  cat("Error:", e$message, "\n")
})