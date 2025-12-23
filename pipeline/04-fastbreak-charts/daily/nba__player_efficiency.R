library(hoopR)
library(dplyr)
library(jsonlite)

# Get current NBA season (NBA season spans two years, e.g., 2024-25)
current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))

# NBA season starts in October, so if we're in Jan-Sep, use previous year as season start
nba_season <- if (current_month >= 10) current_year + 1 else current_year

cat("Processing NBA Player Efficiency for", nba_season - 1, "-", nba_season, "season\n")

# Load player advanced stats using hoopR
player_stats <- tryCatch({
  hoopR::nba_leaguedashplayerstats(
    season = paste0(nba_season - 1, "-", substr(nba_season, 3, 4)),
    measure_type = "Advanced",
    per_mode = "PerGame"
  )$LeagueDashPlayerStats
}, error = function(e) {
  cat("Error loading player stats:", e$message, "\n")
  stop(e)
})

cat("Loaded advanced stats for", nrow(player_stats), "players\n")
cat("Available columns:", paste(names(player_stats), collapse = ", "), "\n")

# NBA division and conference mapping by team
team_divisions <- c(
  "ATL" = "Southeast", "BOS" = "Atlantic", "BKN" = "Atlantic",
  "CHA" = "Southeast", "CHI" = "Central", "CLE" = "Central",
  "DAL" = "Southwest", "DEN" = "Northwest", "DET" = "Central",
  "GSW" = "Pacific", "HOU" = "Southwest", "IND" = "Central",
  "LAC" = "Pacific", "LAL" = "Pacific", "MEM" = "Southwest",
  "MIA" = "Southeast", "MIL" = "Central", "MIN" = "Northwest",
  "NOP" = "Southwest", "NYK" = "Atlantic", "OKC" = "Northwest",
  "ORL" = "Southeast", "PHI" = "Atlantic", "PHX" = "Pacific",
  "POR" = "Northwest", "SAC" = "Pacific", "SAS" = "Southwest",
  "TOR" = "Atlantic", "UTA" = "Northwest", "WAS" = "Southeast"
)

team_conferences <- c(
  "ATL" = "Eastern", "BOS" = "Eastern", "BKN" = "Eastern",
  "CHA" = "Eastern", "CHI" = "Eastern", "CLE" = "Eastern",
  "DAL" = "Western", "DEN" = "Western", "DET" = "Eastern",
  "GSW" = "Western", "HOU" = "Western", "IND" = "Eastern",
  "LAC" = "Western", "LAL" = "Western", "MEM" = "Western",
  "MIA" = "Eastern", "MIL" = "Eastern", "MIN" = "Western",
  "NOP" = "Western", "NYK" = "Eastern", "OKC" = "Western",
  "ORL" = "Eastern", "PHI" = "Eastern", "PHX" = "Western",
  "POR" = "Western", "SAC" = "Western", "SAS" = "Western",
  "TOR" = "Eastern", "UTA" = "Western", "WAS" = "Eastern"
)

# Filter to players with significant playing time (at least 20 MPG and 15 games)
# This ensures we're looking at meaningful sample sizes
min_minutes <- 20
min_games <- 15

qualified_players <- player_stats %>%
  mutate(
    MIN = as.numeric(MIN),
    GP = as.numeric(GP),
    USG_PCT = as.numeric(USG_PCT),
    TS_PCT = as.numeric(TS_PCT),
    PIE = as.numeric(PIE)
  ) %>%
  filter(
    MIN >= min_minutes,
    GP >= min_games,
    !is.na(USG_PCT),
    !is.na(TS_PCT)
  )

cat("\nQualified players (>=", min_minutes, "MPG, >=", min_games, "games):", nrow(qualified_players), "\n")

# Select top 50 players by PIE (Player Impact Estimate) for a cleaner visualization
top_players <- qualified_players %>%
  arrange(desc(PIE)) %>%
  head(50) %>%
  select(PLAYER_NAME, TEAM_ABBREVIATION, USG_PCT, TS_PCT, PIE, GP, MIN)

cat("\nTop Players Preview:\n")
print(head(top_players, 10))

# Convert to list format for JSON matching ScatterPlotVisualization model
# x = Usage Rate (%), y = True Shooting (%)
# Higher in both = elite high-volume scorer
data_points <- top_players %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = PLAYER_NAME,
    x = round(USG_PCT * 100, 1),
    y = round(TS_PCT * 100, 1),
    sum = round(PIE * 100, 1),
    team = TEAM_ABBREVIATION,
    division = team_divisions[TEAM_ABBREVIATION],
    conference = team_conferences[TEAM_ABBREVIATION]
  ))) %>%
  pull(data_point)

# Create output object with metadata matching ScatterPlotVisualization model
output_data <- list(
  sport = "NBA",
  visualizationType = "SCATTER_PLOT",
  title = paste0("NBA Player Efficiency - ", nba_season - 1, "-", substr(nba_season, 3, 4)),
  subtitle = "Usage Rate vs True Shooting %",
  description = "Usage Rate measures the percentage of team plays used by a player while on court, while True Shooting % accounts for 2-pointers, 3-pointers, and free throws to measure scoring efficiency. Players in the top-right quadrant are elite high-volume scorers who maintain efficiency despite heavy usage. Top 50 players by Player Impact Estimate (PIE) shown.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "hoopR / NBA Stats",
  xAxisLabel = "Usage Rate %",
  yAxisLabel = "True Shooting %",
  xColumnLabel = "USG%",
  yColumnLabel = "TS%",
  invertYAxis = FALSE,
  quadrantTopRight = list(color = "#4CAF50", label = "Elite Scorers"),
  quadrantTopLeft = list(color = "#2196F3", label = "Efficient Role Players"),
  quadrantBottomLeft = list(color = "#9E9E9E", label = "Limited Role"),
  quadrantBottomRight = list(color = "#FF9800", label = "Volume Shooters"),
  dataPoints = data_points
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "nba__player_efficiency.json"
} else {
  "dev/nba__player_efficiency.json"
}

# Write JSON to temp file and upload via AWS CLI
tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE)

s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
result <- system(cmd)

if (result != 0) {
  stop("Failed to upload to S3")
}

cat("\nUploaded to S3:", s3_path, "\n")

# Update DynamoDB with updatedAt, title, and interval
dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
chart_title <- output_data$title
chart_interval <- "daily"

dynamodb_item <- sprintf('{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "%s"}}', s3_key, utc_timestamp, chart_title, chart_interval)
dynamodb_cmd <- sprintf(
  'aws dynamodb put-item --table-name %s --item %s',
  shQuote(dynamodb_table),
  shQuote(dynamodb_item)
)

dynamodb_result <- system(dynamodb_cmd)

if (dynamodb_result != 0) {
  warning("Failed to update DynamoDB timestamp (non-fatal)")
} else {
  cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "updatedAt:", utc_timestamp, "title:", chart_title, "interval:", chart_interval, "\n")
}

# Print summary
cat("\nPlayer Efficiency Summary:\n")
cat("Total players shown:", length(data_points), "\n")
cat("\nTop 5 by PIE (Player Impact Estimate):\n")
print(head(top_players %>% select(PLAYER_NAME, TEAM_ABBREVIATION, USG_PCT, TS_PCT, PIE), 5))
cat("\nMost Efficient High-Usage Players (USG > 25%, sorted by TS%):\n")
high_usage_efficient <- top_players %>%
  filter(USG_PCT > 0.25) %>%
  arrange(desc(TS_PCT)) %>%
  head(5)
print(high_usage_efficient %>% select(PLAYER_NAME, TEAM_ABBREVIATION, USG_PCT, TS_PCT))
