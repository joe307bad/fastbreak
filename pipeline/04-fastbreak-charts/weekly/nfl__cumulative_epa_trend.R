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

cat("Processing NFL Cumulative EPA Trend for season:", current_season, "through week:", most_recent_week, "\n")

# Calculate total EPA per team per week (offense - defense for net EPA)
weekly_epa <- pbp %>%
  filter(!is.na(epa), !is.na(posteam), !is.na(defteam)) %>%
  group_by(week, posteam) %>%
  summarise(
    offense_epa = sum(epa, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  rename(team = posteam)

# Get defensive EPA allowed per team per week
defensive_epa <- pbp %>%
  filter(!is.na(epa), !is.na(defteam)) %>%
  group_by(week, defteam) %>%
  summarise(
    defense_epa_allowed = sum(epa, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  rename(team = defteam)

# Combine and calculate net EPA (offense - defense allowed, higher is better)
team_weekly_epa <- weekly_epa %>%
  left_join(defensive_epa, by = c("week", "team")) %>%
  mutate(
    defense_epa_allowed = replace_na(defense_epa_allowed, 0),
    net_epa = offense_epa - defense_epa_allowed
  ) %>%
  filter(!is.na(team) & team != "")

# Calculate cumulative EPA for each team
cumulative_epa <- team_weekly_epa %>%
  arrange(team, week) %>%
  group_by(team) %>%
  mutate(cumulative_net_epa = cumsum(net_epa)) %>%
  ungroup()

# Get final cumulative EPA to find top teams
final_standings <- cumulative_epa %>%
  filter(week == most_recent_week) %>%
  arrange(desc(cumulative_net_epa))

cat("\nTop 10 teams by Cumulative Net EPA:\n")
print(head(final_standings %>% select(team, cumulative_net_epa), 10))

# Select top 4 teams for the line chart
top_teams <- head(final_standings$team, 4)
cat("\nSelected teams for chart:", paste(top_teams, collapse = ", "), "\n")

# Define distinct colors for each series line
# These colors are chosen to be visually distinct and work on both light/dark themes
series_colors <- c(
  "#E91E63",  # Pink
  "#2196F3",  # Blue
  "#4CAF50",  # Green
  "#FF9800"   # Orange
)

# Filter to only top teams and build series data
series_data <- lapply(seq_along(top_teams), function(i) {
  team_name <- top_teams[i]
  team_data <- cumulative_epa %>%
    filter(team == team_name) %>%
    arrange(week)

  # Get team info
  team_meta <- teams_info %>% filter(team_abbr == team_name)

  # Create data points list
  data_points <- lapply(1:nrow(team_data), function(j) {
    list(
      x = as.numeric(team_data$week[j]),
      y = round(team_data$cumulative_net_epa[j], 2)
    )
  })

  list(
    label = team_name,
    color = series_colors[i],
    division = if(nrow(team_meta) > 0) team_meta$team_division else NA,
    conference = if(nrow(team_meta) > 0) team_meta$team_conf else NA,
    dataPoints = data_points
  )
})

# Create output object with metadata matching LineChartVisualization model
output_data <- list(
  sport = "NFL",
  visualizationType = "LINE_CHART",
  title = paste("Cumulative EPA Trend - Week", most_recent_week),
  subtitle = "Top 4 Teams Net EPA Over the Season",
  description = "This chart tracks the cumulative Net EPA (Expected Points Added) for the top 4 NFL teams throughout the season. Net EPA combines offensive production with defensive efficiency - positive values mean the team is outperforming expectations. Teams with steeper upward slopes are playing at an elite level, while flat or declining lines indicate struggles. The gap between lines shows the relative dominance between top teams.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "nflfastR / nflreadr",
  series = series_data
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "nfl__cumulative_epa_trend.json"
} else {
  "dev/nfl__cumulative_epa_trend.json"
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
cat("\nSeries Summary:\n")
for (s in series_data) {
  cat(sprintf("  %s: %d data points, final EPA: %.2f\n",
              s$label,
              length(s$dataPoints),
              s$dataPoints[[length(s$dataPoints)]]$y))
}
