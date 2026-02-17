# College Basketball Team Efficiency
# Reads ORtg and DRtg from sports-ref-top-teams.csv
# Usage: Rscript cbb__team_efficiency.R [year]

library(dplyr)
library(jsonlite)
library(readr)

# Get year from command line args, default to current year
args <- commandArgs(trailingOnly = TRUE)
current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))
default_year <- if (current_month >= 10) current_year + 1 else current_year

year <- if (length(args) >= 1) as.integer(args[1]) else default_year

# Data directory - use /app/data in container, or relative path for local dev
data_base <- if (dir.exists("/app/data")) {
  "/app/data"
} else {
  # Local development: look for data folder relative to script
  cmd_args <- commandArgs(trailingOnly = FALSE)
  script_path <- sub("--file=", "", cmd_args[grep("--file=", cmd_args)])
  script_dir <- dirname(script_path)
  file.path(dirname(script_dir), "data")
}

# Build path
data_dir <- file.path(data_base, "cbb", year)
csv_path <- file.path(data_dir, "sports-ref-top-teams.csv")

cat("Processing College Basketball Team Efficiency for", year, "\n")
cat("CSV path:", csv_path, "\n\n")

# Read from sports-ref-top-teams.csv
# Row 1: group headers, Row 2: column names, Row 3+: data
# Columns: X2=School, X3=Conf, X17=ORtg, X18=DRtg, X19=NRtg
csv_data <- read_csv(csv_path, skip = 2, col_names = FALSE, show_col_types = FALSE)

team_ratings <- csv_data %>%
  select(team_name = X2, conference = X3, off_rating = X17, def_rating = X18) %>%
  filter(!is.na(team_name) & team_name != "School") %>%
  mutate(
    off_rating = as.numeric(off_rating),
    def_rating = as.numeric(def_rating),
    net_rating = off_rating - def_rating
  ) %>%
  filter(!is.na(off_rating) & !is.na(def_rating))

cat("Loaded", nrow(team_ratings), "teams\n")

cat("\nTop 10 by Net Rating:\n")
print(head(team_ratings %>%
  arrange(desc(net_rating)) %>%
  select(team_name, off_rating, def_rating, net_rating), 10))

# Convert to list format for JSON matching ScatterPlotVisualization model
data_points <- team_ratings %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = team_name,
    x = round(off_rating, 2),
    y = round(def_rating, 2),
    sum = round(net_rating, 2),
    conference = conference
  ))) %>%
  pull(data_point)

# Create output object
output_data <- list(
  sport = "CBB",
  visualizationType = "SCATTER_PLOT",
  title = paste0("College Basketball Team Efficiency - ", year - 1, "-", substr(year, 3, 4)),
  subtitle = "Offensive vs Defensive Rating",
  description = "Offensive Rating measures points scored per 100 possessions, while Defensive Rating measures points allowed per 100 possessions (lower is better). Teams in the top-right quadrant have elite offenses and defenses.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "Sports Reference",
  xAxisLabel = "Offensive Rating",
  yAxisLabel = "Defensive Rating",
  xColumnLabel = "ORtg",
  yColumnLabel = "DRtg",
  invertYAxis = TRUE,
  quadrantTopRight = list(color = "#4CAF50", label = "Elite"),
  quadrantTopLeft = list(color = "#2196F3", label = "Defense First"),
  quadrantBottomLeft = list(color = "#F44336", label = "Struggling"),
  quadrantBottomRight = list(color = "#FFEB3B", label = "Offense First"),
  subject = "TEAM",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  dataPoints = data_points
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "cbb__team_efficiency.json"
} else {
  "dev/cbb__team_efficiency.json"
}

# Write JSON to temp file and upload via AWS CLI
tmp_file <- tempfile(fileext = ".json")
write_json(
  output_data, tmp_file,
  pretty = TRUE, auto_unbox = TRUE, null = "null"
)

s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
result <- system(cmd)

if (result != 0) {
  stop("Failed to upload to S3")
}

cat("\nUploaded to S3:", s3_path, "\n")

# Update DynamoDB
dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
chart_title <- output_data$title
chart_interval <- "never"

dynamodb_item <- sprintf(
  '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "%s"}}',
  s3_key, utc_timestamp, chart_title, chart_interval
)
dynamodb_cmd <- sprintf(
  'aws dynamodb put-item --table-name %s --item %s',
  shQuote(dynamodb_table),
  shQuote(dynamodb_item)
)

dynamodb_result <- system(dynamodb_cmd)

if (dynamodb_result != 0) {
  warning("Failed to update DynamoDB timestamp (non-fatal)")
} else {
  cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
}

cat("\nTotal teams in output:", length(data_points), "\n")
cat("Done.\n")
