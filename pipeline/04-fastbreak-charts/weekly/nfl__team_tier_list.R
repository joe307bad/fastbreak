library(nflreadr)
library(dplyr)
library(jsonlite)

# Get current season and most recent week
current_season <- as.numeric(format(Sys.Date(), "%Y"))
pbp <- nflreadr::load_pbp(current_season)

# Get the most recent week with data
most_recent_week <- max(pbp$week, na.rm = TRUE)

cat("Processing NFL data for season:", current_season, "week:", most_recent_week, "\n")

# Calculate offensive EPA per play by team
offense_epa <- pbp %>%
  filter(week <= most_recent_week, !is.na(epa), !is.na(posteam)) %>%
  group_by(posteam) %>%
  summarise(offense_epa_per_play = mean(epa, na.rm = TRUE), .groups = "drop") %>%
  rename(team = posteam)

# Calculate defensive EPA per play by team
defense_epa <- pbp %>%
  filter(week <= most_recent_week, !is.na(epa), !is.na(defteam)) %>%
  group_by(defteam) %>%
  summarise(defense_epa_per_play = mean(epa, na.rm = TRUE), .groups = "drop") %>%
  rename(team = defteam)

# Combine offense and defense EPA
team_epa <- offense_epa %>%
  left_join(defense_epa, by = "team") %>%
  arrange(team)

# Convert to list format for JSON matching ScatterPlotVisualization model
# ScatterPlotDataPoint: label, x, y, sum
data_points <- team_epa %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = team,
    x = round(offense_epa_per_play, 4),
    y = round(defense_epa_per_play, 4),
    sum = round(offense_epa_per_play + defense_epa_per_play, 4)
  ))) %>%
  pull(data_point)

# Create output object with metadata matching ScatterPlotVisualization model
output_data <- list(
  sport = "NFL",
  visualizationType = "SCATTER_PLOT",
  title = paste("NFL Team Tier List - Week", most_recent_week),
  subtitle = "Offensive vs Defensive EPA Analysis",
  description = "Expected Points Added (EPA) measures the value of each play by comparing the expected points before and after the play. Offensive EPA per play shows how many points a team adds per offensive play on average, while defensive EPA per play (where lower is better) shows how many points a team allows per defensive play. Teams in the top-right quadrant have strong offenses and defenses, making them the most dominant teams.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  xAxisLabel = "Offensive EPA per Play",
  yAxisLabel = "Defensive EPA per Play",
  xColumnLabel = "OEPA",
  yColumnLabel = "DEPA",
  invertYAxis = TRUE,
  quadrantTopRight = list(color = "#4CAF50", label = "Good D/Good O"),
  quadrantTopLeft = list(color = "#2196F3", label = "Good D/Bad O"),
  quadrantBottomLeft = list(color = "#F44336", label = "Bad D/Bad O"),
  quadrantBottomRight = list(color = "#FFEB3B", label = "Good O/Bad D"),
  source = "nflfastR / nflreadr",
  dataPoints = data_points
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "nfl__team_tier_list.json"
} else {
  "dev/nfl__team_tier_list.json"
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

cat("Uploaded to S3:", s3_path, "\n")

# Update DynamoDB with updatedAt, title, and interval
dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
chart_title <- output_data$title
chart_interval <- "weekly"

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

cat("Total teams:", length(data_points), "\n")
