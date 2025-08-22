# Debug script to check exact column names in pitching data
source("config/setup.R")

cat("=== Checking Pitching Data Columns ===\n")

tryCatch({
  pitching_data <- bref_daily_pitcher(
    t1 = "2024-03-28",
    t2 = "2024-04-01"
  )
  
  cat("All column names in pitching data:\n")
  col_names <- names(pitching_data)
  for (i in seq_along(col_names)) {
    cat(sprintf("%2d: %s\n", i, col_names[i]))
  }
  
  cat("\nLooking for ERA+ and FIP columns:\n")
  era_fip_cols <- col_names[grepl("era|fip", col_names, ignore.case = TRUE)]
  cat("Found:", paste(era_fip_cols, collapse = ", "), "\n")
  
  # Check sample data for Chicago team
  chicago_data <- pitching_data %>% filter(Team == "Chicago")
  cat(sprintf("\nChicago team pitching data: %d rows\n", nrow(chicago_data)))
  if (nrow(chicago_data) > 0) {
    cat("Sample Chicago pitcher:\n")
    print(chicago_data[1, c("Name", "Team", "ERA", "IP")])
  }
  
}, error = function(e) {
  cat("Error:", e$message, "\n")
})