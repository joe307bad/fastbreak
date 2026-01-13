library(httr)
library(dplyr)
library(jsonlite)

# Get current NHL season (NHL season spans two years, e.g., 2024-25)
current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))

# NHL season starts in October
# NHL API expects season format like 20242025
nhl_season_end <- if (current_month >= 10) current_year + 1 else current_year
nhl_season_start <- nhl_season_end - 1
nhl_season_id <- paste0(nhl_season_start, nhl_season_end)

cat("Processing NHL Player Scoring for", nhl_season_start, "-", nhl_season_end, "season\n")

# Load skater stats using NHL API
skater_stats <- tryCatch({
  api_url <- sprintf(
    "https://api.nhle.com/stats/rest/en/skater/summary?isAggregate=false&isGame=false&limit=-1&cayenneExp=seasonId=%s%%20and%%20gameTypeId=2",
    nhl_season_id
  )
  cat("Fetching from NHL API:", api_url, "\n")

  response <- GET(api_url)
  if (status_code(response) != 200) {
    stop(sprintf("NHL API returned status %d", status_code(response)))
  }

  result <- fromJSON(content(response, "text", encoding = "UTF-8"))
  if (is.null(result$data) || nrow(result$data) == 0) {
    stop("NHL API returned empty data")
  }
  result$data
}, error = function(e) {
  cat("Error loading skater stats:", e$message, "\n")
  stop(e)
})

cat("Loaded stats for", nrow(skater_stats), "skaters\n")
cat("Available columns:", paste(names(skater_stats), collapse = ", "), "\n")
cat("First row sample:\n")
print(head(skater_stats, 1))

# NHL division and conference mapping by team
team_divisions <- c(
  "ANA" = "Pacific", "ARI" = "Central", "BOS" = "Atlantic",
  "BUF" = "Atlantic", "CGY" = "Pacific", "CAR" = "Metropolitan",
  "CHI" = "Central", "COL" = "Central", "CBJ" = "Metropolitan",
  "DAL" = "Central", "DET" = "Atlantic", "EDM" = "Pacific",
  "FLA" = "Atlantic", "LAK" = "Pacific", "MIN" = "Central",
  "MTL" = "Atlantic", "NSH" = "Central", "NJD" = "Metropolitan",
  "NYI" = "Metropolitan", "NYR" = "Metropolitan", "OTT" = "Atlantic",
  "PHI" = "Metropolitan", "PIT" = "Metropolitan", "SJS" = "Pacific",
  "SEA" = "Pacific", "STL" = "Central", "TBL" = "Atlantic",
  "TOR" = "Atlantic", "UTA" = "Central", "VAN" = "Pacific",
  "VGK" = "Pacific", "WSH" = "Metropolitan", "WPG" = "Central"
)

team_conferences <- c(
  "ANA" = "Western", "ARI" = "Western", "BOS" = "Eastern",
  "BUF" = "Eastern", "CGY" = "Western", "CAR" = "Eastern",
  "CHI" = "Western", "COL" = "Western", "CBJ" = "Eastern",
  "DAL" = "Western", "DET" = "Eastern", "EDM" = "Western",
  "FLA" = "Eastern", "LAK" = "Western", "MIN" = "Western",
  "MTL" = "Eastern", "NSH" = "Western", "NJD" = "Eastern",
  "NYI" = "Eastern", "NYR" = "Eastern", "OTT" = "Eastern",
  "PHI" = "Eastern", "PIT" = "Eastern", "SJS" = "Western",
  "SEA" = "Western", "STL" = "Western", "TBL" = "Eastern",
  "TOR" = "Eastern", "UTA" = "Western", "VAN" = "Western",
  "VGK" = "Western", "WSH" = "Eastern", "WPG" = "Western"
)

# Filter to players with significant playing time (at least 20 games)
# and get top scorers by points
min_games <- 20

qualified_players <- skater_stats %>%
  mutate(
    GP = as.numeric(gamesPlayed),
    G = as.numeric(goals),
    A = as.numeric(assists),
    PTS = as.numeric(points),
    player = skaterFullName,
    team = teamAbbrevs
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
  mutate(
    team_code = team,
    team_division = unname(team_divisions[team]),
    team_conference = unname(team_conferences[team]),
    data_point = list(list(
      label = player,
      x = G,
      y = A,
      sum = PTS,
      teamCode = team_code,
      division = if (!is.na(team_division)) team_division else NULL,
      conference = if (!is.na(team_conference)) team_conference else NULL
    ))
  ) %>%
  pull(data_point)

# Create output object with metadata matching ScatterPlotVisualization model
output_data <- list(
  sport = "NHL",
  visualizationType = "SCATTER_PLOT",
  title = paste0("NHL Scoring Leaders - ", nhl_season_start, "-", substr(nhl_season_end, 3, 4)),
  subtitle = "Goals vs Assists",
  description = "This chart shows the top 50 NHL scorers plotted by their goals and assists. Players in the top-right are complete offensive players who both score and create. Top-left are elite playmakers who primarily set up teammates. Bottom-right are pure goal scorers. The sum represents total points.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "NHL Stats API",
  xAxisLabel = "Goals",
  yAxisLabel = "Assists",
  xColumnLabel = "G",
  yColumnLabel = "A",
  invertYAxis = FALSE,
  quadrantTopRight = list(color = "#4CAF50", label = "Complete Players"),
  quadrantTopLeft = list(color = "#2196F3", label = "Playmakers"),
  quadrantBottomLeft = list(color = "#9E9E9E", label = "Role Players"),
  quadrantBottomRight = list(color = "#FF9800", label = "Goal Scorers"),
  subject = "PLAYER",
  tags = c("regular season", "player"),
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
