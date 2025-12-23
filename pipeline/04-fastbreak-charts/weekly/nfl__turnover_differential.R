library(nflreadr)
library(dplyr)
library(tidyr)
library(jsonlite)

# Get current season and most recent week
current_season <- as.numeric(format(Sys.Date(), "%Y"))
pbp <- nflreadr::load_pbp(current_season)

# Load team info for division and conference data
teams_info <- nflreadr::load_teams() %>%
  select(team_abbr, team_conf, team_division)

# Get the most recent week with data
most_recent_week <- max(pbp$week, na.rm = TRUE)

cat("Processing NFL Turnover Differential for season:", current_season, "week:", most_recent_week, "\n")

# Calculate turnovers forced (defensive turnovers)
turnovers_forced <- pbp %>%
  filter(week <= most_recent_week) %>%
  filter(interception == 1 | fumble_lost == 1) %>%
  group_by(defteam) %>%
  summarise(turnovers_forced = n(), .groups = "drop") %>%
  rename(team = defteam)

# Calculate turnovers committed (offensive turnovers)
turnovers_committed <- pbp %>%
  filter(week <= most_recent_week) %>%
  filter(interception == 1 | fumble_lost == 1) %>%
  group_by(posteam) %>%
  summarise(turnovers_committed = n(), .groups = "drop") %>%
  rename(team = posteam)

# Combine and calculate differential
turnover_diff <- turnovers_forced %>%
  full_join(turnovers_committed, by = "team") %>%
  left_join(teams_info, by = c("team" = "team_abbr")) %>%
  mutate(
    turnovers_forced = replace_na(turnovers_forced, 0),
    turnovers_committed = replace_na(turnovers_committed, 0),
    differential = turnovers_forced - turnovers_committed
  ) %>%
  filter(!is.na(team) & team != "") %>%
  arrange(desc(differential))

cat("Teams processed:", nrow(turnover_diff), "\n")

# Convert to list format for JSON matching BarGraphVisualization model
# BarGraphDataPoint: label, value, division, conference
data_points <- turnover_diff %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = team,
    value = as.numeric(differential),
    division = team_division,
    conference = team_conf
  ))) %>%
  pull(data_point)

# Create output object with metadata matching BarGraphVisualization model
output_data <- list(
  sport = "NFL",
  visualizationType = "BAR_GRAPH",
  title = paste("Turnover Differential - Week", most_recent_week),
  subtitle = "Turnovers Forced minus Turnovers Committed",
  description = "Turnover differential measures a team's ability to protect the ball while taking it away from opponents. Positive values indicate a team forces more turnovers than they commit, which strongly correlates with winning. Teams at the top are winning the turnover battle, while teams at the bottom are giving the ball away more than they're taking it.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
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
  "nfl__turnover_differential.json"
} else {
  "dev/nfl__turnover_differential.json"
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

# Print summary
cat("\nTurnover Differential Summary:\n")
cat("Top 5 teams:\n")
print(head(turnover_diff %>% select(team, turnovers_forced, turnovers_committed, differential), 5))
cat("\nBottom 5 teams:\n")
print(tail(turnover_diff %>% select(team, turnovers_forced, turnovers_committed, differential), 5))
cat("\nTotal teams:", length(data_points), "\n")
