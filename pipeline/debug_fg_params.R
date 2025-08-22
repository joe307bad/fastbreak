# Debug script to check FanGraphs function parameters
source("config/setup.R")

cat("=== Checking FanGraphs Function Parameters ===\n")

# Check fg_team_batter parameters
cat("fg_team_batter function help:\n")
tryCatch({
  cat("Function arguments:\n")
  cat(paste(names(formals(fg_team_batter)), collapse = ", "), "\n")
}, error = function(e) {
  cat("Error getting fg_team_batter parameters:", e$message, "\n")
})

cat("\nfg_team_pitcher function help:\n")
tryCatch({
  cat("Function arguments:\n")
  cat(paste(names(formals(fg_team_pitcher)), collapse = ", "), "\n")
}, error = function(e) {
  cat("Error getting fg_team_pitcher parameters:", e$message, "\n")
})

# Try a simple call to see what happens
cat("\nTrying simple fg_team_batter call:\n")
tryCatch({
  result <- fg_team_batter()
  cat("Success! Rows:", nrow(result), "\n")
  cat("Columns:", paste(names(result), collapse = ", "), "\n")
}, error = function(e) {
  cat("Error:", e$message, "\n")
})