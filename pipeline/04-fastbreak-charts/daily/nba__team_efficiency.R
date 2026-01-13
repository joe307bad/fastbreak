library(hoopR)
library(dplyr)
library(jsonlite)

# Get current NBA season (NBA season spans two years, e.g., 2024-25)
current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))

# NBA season starts in October, so if we're in Jan-Sep, use previous year as season start
nba_season <- if (current_month >= 10) current_year + 1 else current_year

cat("Processing NBA Team Efficiency for", nba_season - 1, "-", nba_season, "season\n")

# Load team standings/stats using hoopR
# nba_standings gives us team records and ratings
standings <- tryCatch({
  hoopR::nba_leaguestandings(season = nba_season)$Standings
}, error = function(e) {
  cat("Error loading standings:", e$message, "\n")
  stop(e)
})

cat("Loaded standings for", nrow(standings), "teams\n")

# Get team stats for offensive and defensive ratings
team_stats <- tryCatch({
  hoopR::nba_leaguedashteamstats(
    season = paste0(nba_season - 1, "-", substr(nba_season, 3, 4)),
    measure_type = "Advanced"
  )$LeagueDashTeamStats
}, error = function(e) {
  cat("Error loading team stats:", e$message, "\n
")
  stop(e)
})

cat("Loaded advanced stats for", nrow(team_stats), "teams\n")
cat("Available columns:", paste(names(team_stats), collapse = ", "), "\n")

# NBA team abbreviation mapping
team_abbrevs <- c(
  "Atlanta Hawks" = "ATL", "Boston Celtics" = "BOS", "Brooklyn Nets" = "BKN",
  "Charlotte Hornets" = "CHA", "Chicago Bulls" = "CHI", "Cleveland Cavaliers" = "CLE",
  "Dallas Mavericks" = "DAL", "Denver Nuggets" = "DEN", "Detroit Pistons" = "DET",
  "Golden State Warriors" = "GSW", "Houston Rockets" = "HOU", "Indiana Pacers" = "IND",
  "LA Clippers" = "LAC", "Los Angeles Clippers" = "LAC", "Los Angeles Lakers" = "LAL",
  "Memphis Grizzlies" = "MEM", "Miami Heat" = "MIA", "Milwaukee Bucks" = "MIL",
  "Minnesota Timberwolves" = "MIN", "New Orleans Pelicans" = "NOP",
  "New York Knicks" = "NYK", "Oklahoma City Thunder" = "OKC", "Orlando Magic" = "ORL",
  "Philadelphia 76ers" = "PHI", "Phoenix Suns" = "PHX", "Portland Trail Blazers" = "POR",
  "Sacramento Kings" = "SAC", "San Antonio Spurs" = "SAS", "Toronto Raptors" = "TOR",
  "Utah Jazz" = "UTA", "Washington Wizards" = "WAS"
)

# NBA division and conference mapping
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

# Extract offensive and defensive ratings
# OFF_RATING = points scored per 100 possessions
# DEF_RATING = points allowed per 100 possessions (lower is better)
team_ratings <- team_stats %>%
  select(TEAM_NAME, TEAM_ID, OFF_RATING, DEF_RATING, NET_RATING) %>%
  mutate(
    TEAM_ABBREV = team_abbrevs[TEAM_NAME],
    OFF_RATING = as.numeric(OFF_RATING),
    DEF_RATING = as.numeric(DEF_RATING),
    NET_RATING = as.numeric(NET_RATING)
  ) %>%
  filter(!is.na(OFF_RATING) & !is.na(DEF_RATING))

cat("\nTeam Ratings Preview:\n")
print(head(team_ratings %>% arrange(desc(NET_RATING)), 10))

# Convert to list format for JSON matching ScatterPlotVisualization model
# For NBA: x = Offensive Rating, y = Defensive Rating
# invertYAxis = TRUE because lower defensive rating is better
data_points <- team_ratings %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = TEAM_ABBREV,
    x = round(OFF_RATING, 2),
    y = round(DEF_RATING, 2),
    sum = round(NET_RATING, 2),
    division = team_divisions[TEAM_ABBREV],
    conference = team_conferences[TEAM_ABBREV]
  ))) %>%
  pull(data_point)

# Create output object with metadata matching ScatterPlotVisualization model
output_data <- list(
  sport = "NBA",
  visualizationType = "SCATTER_PLOT",
  title = paste0("NBA Team Efficiency - ", nba_season - 1, "-", substr(nba_season, 3, 4)),
  subtitle = "Offensive vs Defensive Rating",
  description = "Offensive Rating measures points scored per 100 possessions, while Defensive Rating measures points allowed per 100 possessions (lower is better). Teams in the top-right quadrant have elite offenses and defenses, making them championship contenders. Net Rating (the difference) is the best single measure of team quality.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "hoopR / NBA Stats",
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
  tags = c("regular season", "team"),
  dataPoints = data_points
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "nba__team_efficiency.json"
} else {
  "dev/nba__team_efficiency.json"
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
cat("\nTeam Efficiency Summary:\n")
cat("Total teams:", length(data_points), "\n")
cat("\nTop 5 by Net Rating:\n")
print(head(team_ratings %>% arrange(desc(NET_RATING)) %>% select(TEAM_ABBREV, OFF_RATING, DEF_RATING, NET_RATING), 5))
cat("\nBottom 5 by Net Rating:\n")
print(tail(team_ratings %>% arrange(desc(NET_RATING)) %>% select(TEAM_ABBREV, OFF_RATING, DEF_RATING, NET_RATING), 5))
