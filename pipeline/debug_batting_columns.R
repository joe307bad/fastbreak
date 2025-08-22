# Debug script to check exact column names in batting data
source("config/setup.R")

cat("=== Checking Batting Data Columns ===\n")

tryCatch({
  batting_data <- bref_daily_batter(
    t1 = "2024-03-28",
    t2 = "2024-04-01"
  )
  
  cat("All column names in batting data:\n")
  col_names <- names(batting_data)
  for (i in seq_along(col_names)) {
    cat(sprintf("%2d: %s\n", i, col_names[i]))
  }
  
  cat("\nLooking for wOBA-like columns:\n")
  woba_cols <- col_names[grepl("woba|ops|obp|slg", col_names, ignore.case = TRUE)]
  cat("Found:", paste(woba_cols, collapse = ", "), "\n")
  
  # Check sample data for Chicago team
  chicago_data <- batting_data %>% filter(Team == "Chicago")
  cat(sprintf("\nChicago team data: %d rows\n", nrow(chicago_data)))
  if (nrow(chicago_data) > 0) {
    cat("Sample Chicago player:\n")
    print(chicago_data[1, c("Name", "Team", "OPS", "AB", "PA")])
  }
  
}, error = function(e) {
  cat("Error:", e$message, "\n")
})