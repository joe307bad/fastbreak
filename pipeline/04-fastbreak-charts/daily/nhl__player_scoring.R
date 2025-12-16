library(hockeyR)
library(dplyr)
library(jsonlite)

# Get current NHL season (NHL season spans two years, e.g., 2024-25)
current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))

# NHL season starts in October
# get_skater_stats_hr expects the END year of the season (e.g., 2025 for 2024-25 season)
nhl_season_end <- if (current_month >= 10) current_year + 1 else current_year
nhl_season_start <- nhl_season_end - 1

cat("Processing NHL Player Scoring for", nhl_season_start, "-", nhl_season_end, "season\n")

# Load skater stats using hockeyR
skater_stats <- tryCatch({
  result <- hockeyR::get_skater_stats_hr(season = nhl_season_end)
  if (is.null(result) || nrow(result) == 0) {
    stop("get_skater_stats_hr returned empty data")
  }
  result
}, error = function(e) {
  cat("Error loading skater stats:", e$message, "\n")
  stop(e)
})

cat("Loaded stats for", nrow(skater_stats), "skaters\n")
cat("Available columns:", paste(names(skater_stats), collapse = ", "), "\n")
cat("First row sample:\n")
print(head(skater_stats, 1))

# Filter to players with significant playing time (at least 20 games)
# and get top scorers by points
min_games <- 20

qualified_players <- skater_stats %>%
  mutate(
    GP = as.numeric(games),
    G = as.numeric(goals),
    A = as.numeric(assists),
    PTS = as.numeric(points)
  ) %>%
  filter(
    GP >= min_games,
    !is.na(G),
    !is.na(A)
  )

cat("\nQualified players (>=", min_games, "games):", nrow(qualified_players), "\n")

# Select top 50 players by points for a cleaner visualization
top_players <- qualified_players %>%
  arrange(desc(PTS)) %>%
  head(50) %>%
  select(player, team, GP, G, A, PTS)

cat("\nTop Players Preview:\n")
print(head(top_players, 10))

# Convert to list format for JSON matching ScatterPlotVisualization model
# x = Goals, y = Assists
# Top-right = complete players (high goals + high assists)
# Top-left = playmakers (high assists, lower goals)
# Bottom-right = pure goal scorers (high goals, lower assists)
data_points <- top_players %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = player,
    x = G,
    y = A,
    sum = PTS
  ))) %>%
  pull(data_point)

# Create output object with metadata matching ScatterPlotVisualization model
output_data <- list(
  sport = "NHL",
  visualizationType = "SCATTER_PLOT",
  title = paste0("NHL Scoring Leaders - ", nhl_season_start, "-", substr(nhl_season_end, 3, 4)),
  subtitle = "Goals vs Assists",
  description = "This chart shows the top 50 NHL scorers plotted by their goals and assists. Players in the top-right are complete offensive players who both score and create. Top-left are elite playmakers who primarily set up teammates. Bottom-right are pure goal scorers. The sum represents total points.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "hockeyR / Hockey Reference",
  xAxisLabel = "Goals",
  yAxisLabel = "Assists",
  xColumnLabel = "G",
  yColumnLabel = "A",
  invertYAxis = FALSE,
  quadrantTopRight = list(color = "#4CAF50", label = "Complete Players"),
  quadrantTopLeft = list(color = "#2196F3", label = "Playmakers"),
  quadrantBottomLeft = list(color = "#9E9E9E", label = "Role Players"),
  quadrantBottomRight = list(color = "#FF9800", label = "Goal Scorers"),
  dataPoints = data_points
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "nhl__player_scoring.json"
} else {
  "dev/nhl__player_scoring.json"
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
cat("\nPlayer Scoring Summary:\n")
cat("Total players shown:", length(data_points), "\n")
cat("\nTop 5 by Points:\n")
print(head(top_players %>% select(player, team, G, A, PTS), 5))
cat("\nTop Goal Scorers:\n")
print(head(top_players %>% arrange(desc(G)) %>% select(player, team, G, A, PTS), 5))
cat("\nTop Playmakers:\n")
print(head(top_players %>% arrange(desc(A)) %>% select(player, team, G, A, PTS), 5))
