# Debug script to examine team names in batting data
source("config/setup.R")

cat("=== Debugging Team Names in Batting Data ===\n")

tryCatch({
  # Get batting data
  batting_data <- bref_daily_batter(
    t1 = "2024-03-28",
    t2 = "2024-04-01"
  )
  
  cat("Unique teams in batting data:\n")
  unique_teams <- unique(batting_data$Team)
  cat(paste(unique_teams, collapse = ", "), "\n\n")
  
  # Look for Chicago White Sox
  cat("Looking for Chicago White Sox variations:\n")
  cws_matches <- unique_teams[grepl("chi|white|sox", unique_teams, ignore.case = TRUE)]
  cat("Matches:", paste(cws_matches, collapse = ", "), "\n\n")
  
  # Look for Atlanta Braves
  cat("Looking for Atlanta Braves variations:\n")
  atl_matches <- unique_teams[grepl("atl|braves", unique_teams, ignore.case = TRUE)]
  cat("Matches:", paste(atl_matches, collapse = ", "), "\n\n")
  
  # Show all unique teams for reference
  cat("All unique teams (", length(unique_teams), "):\n")
  for (i in seq_along(unique_teams)) {
    cat(sprintf("%2d: %s\n", i, unique_teams[i]))
  }
  
}, error = function(e) {
  cat("Error:", e$message, "\n")
})