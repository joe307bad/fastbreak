# Debug script to examine batting data structure
source("config/setup.R")

cat("=== Debugging Batting Data Structure ===\n")

tryCatch({
  # Get batting data like the main script does
  batting_data <- bref_daily_batter(
    t1 = "2024-03-28",
    t2 = "2024-04-01"
  )
  
  cat("Batting data retrieved. Rows:", nrow(batting_data), "\n")
  cat("Columns:", ncol(batting_data), "\n")
  cat("Column names:\n")
  cat(paste(names(batting_data), collapse = ", "), "\n\n")
  
  # Check for team column
  team_cols <- names(batting_data)[grepl("team|tm", names(batting_data), ignore.case = TRUE)]
  cat("Team-related columns:", paste(team_cols, collapse = ", "), "\n\n")
  
  # Look at first few rows
  cat("First 3 rows of key columns:\n")
  if ("Tm" %in% names(batting_data)) {
    print(batting_data[1:min(3, nrow(batting_data)), c("Name", "Tm", "OPS", "AB", "PA")])
  } else if (length(team_cols) > 0) {
    key_cols <- c("Name", team_cols[1], "OPS", "AB", "PA")
    key_cols <- key_cols[key_cols %in% names(batting_data)]
    print(batting_data[1:min(3, nrow(batting_data)), key_cols])
  } else {
    print(batting_data[1:min(3, nrow(batting_data)), 1:min(5, ncol(batting_data))])
  }
  
  # Check unique teams if Tm column exists
  if ("Tm" %in% names(batting_data)) {
    unique_teams <- unique(batting_data$Tm)
    cat("\nUnique teams in Tm column:\n")
    cat(paste(head(unique_teams, 10), collapse = ", "), "\n")
    
    # Look for Chicago White Sox variations
    cws_teams <- unique_teams[grepl("chi|white|sox|cws", unique_teams, ignore.case = TRUE)]
    cat("Teams matching Chicago/White/Sox/CWS:\n")
    cat(paste(cws_teams, collapse = ", "), "\n")
  }
  
}, error = function(e) {
  cat("Error:", e$message, "\n")
})