library(jsonlite)

# Generate weekly report data
weekly_data <- list(
  report_type = "weekly",
  timestamp = format(Sys.time(), "%Y-%m-%d %H:%M:%S"),
  week = format(Sys.time(), "%Y-W%U"),
  data = list(
    metric_1 = runif(1, 100, 200),
    metric_2 = runif(1, 50, 100),
    status = "success"
  )
)

# Write to output directory
output_file <- file.path("/app/output", paste0("weekly_report_", format(Sys.time(), "%Y%m%d_%H%M%S"), ".json"))
write_json(weekly_data, output_file, pretty = TRUE, auto_unbox = TRUE)

cat("Weekly report generated:", output_file, "\n")
