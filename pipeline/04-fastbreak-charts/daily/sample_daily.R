library(jsonlite)

# Generate daily report data
daily_data <- list(
  report_type = "daily",
  timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
  date = format(Sys.time(), "%Y-%m-%d"),
  data = list(
    events_count = sample(10:50, 1),
    avg_duration = runif(1, 1, 10),
    status = "success"
  )
)

# Write to output directory
output_file <- file.path("/app/output", paste0("daily_report_", format(Sys.time(), "%Y%m%d_%H%M%S"), ".json"))
write_json(daily_data, output_file, pretty = TRUE, auto_unbox = TRUE)

cat("Daily report generated:", output_file, "\n")
