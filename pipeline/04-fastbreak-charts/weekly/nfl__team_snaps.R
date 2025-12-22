library(nflreadr)
library(dplyr)
library(jsonlite)

# Get current season and most recent week
current_season <- as.numeric(format(Sys.Date(), "%Y"))
pbp <- nflreadr::load_pbp(current_season)

# Get the most recent week with data
most_recent_week <- max(pbp$week, na.rm = TRUE)

cat("Processing NFL snap data for season:", current_season, "week:", most_recent_week, "\n")

# Calculate total offensive snaps by team (plays where team is on offense)
offense_snaps <- pbp %>%
  filter(week <= most_recent_week, !is.na(posteam)) %>%
  group_by(posteam) %>%
  summarise(offense_snaps = n(), .groups = "drop") %>%
  rename(team = posteam)

# Calculate total defensive snaps by team (plays where team is on defense)
defense_snaps <- pbp %>%
  filter(week <= most_recent_week, !is.na(defteam)) %>%
  group_by(defteam) %>%
  summarise(defense_snaps = n(), .groups = "drop") %>%
  rename(team = defteam)

# Combine offense and defense snaps
team_snaps <- offense_snaps %>%
  left_join(defense_snaps, by = "team") %>%
  arrange(team)

# Convert to list format for JSON matching ScatterPlotVisualization model
# ScatterPlotDataPoint: label, x, y, sum
data_points <- team_snaps %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = team,
    x = as.integer(defense_snaps),
    y = as.integer(offense_snaps),
    sum = as.integer(offense_snaps + defense_snaps)
  ))) %>%
  pull(data_point)

# Create output object with metadata matching ScatterPlotVisualization model
output_data <- list(
  sport = "NFL",
  visualizationType = "SCATTER_PLOT",
  title = paste("NFL Team Snaps - Week", most_recent_week),
  subtitle = "Total Offensive vs Defensive Snaps",
  description = "This chart shows the total number of offensive and defensive snaps each team has played through the season. More snaps generally indicate longer, more competitive games and stronger time of possession. Teams with significantly more defensive snaps than offensive snaps may be struggling to maintain possession, while teams with more offensive snaps are likely controlling the game tempo.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  xAxisLabel = "Defensive Snaps",
  yAxisLabel = "Offensive Snaps",
  xColumnLabel = "Def Snaps",
  yColumnLabel = "Off Snaps",
  invertYAxis = FALSE,
  quadrantTopRight = list(color = "#9C27B0", label = "High D/High O"),
  quadrantTopLeft = list(color = "#2196F3", label = "Low D/High O"),
  quadrantBottomLeft = list(color = "#F44336", label = "Low D/Low O"),
  quadrantBottomRight = list(color = "#FF9800", label = "High D/Low O"),
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
  "nfl__team_snaps.json"
} else {
  "dev/nfl__team_snaps.json"
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
