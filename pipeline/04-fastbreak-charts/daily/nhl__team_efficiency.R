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

cat("Processing NHL Team Efficiency for", nhl_season_start, "-", nhl_season_end, "season\n")

# Load team stats using NHL API
standings <- tryCatch({
  api_url <- sprintf(
    "https://api.nhle.com/stats/rest/en/team/summary?cayenneExp=seasonId=%s%%20and%%20gameTypeId=2",
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
  cat("Error loading standings:", e$message, "\n")
  stop(e)
})

cat("Loaded standings for", nrow(standings), "teams\n")
cat("Available columns:", paste(names(standings), collapse = ", "), "\n")
cat("First row sample:\n")
print(head(standings, 1))

# NHL team abbreviation mapping
team_abbrevs <- c(
  "Anaheim Ducks" = "ANA", "Arizona Coyotes" = "ARI", "Boston Bruins" = "BOS",
  "Buffalo Sabres" = "BUF", "Calgary Flames" = "CGY", "Carolina Hurricanes" = "CAR",
  "Chicago Blackhawks" = "CHI", "Colorado Avalanche" = "COL", "Columbus Blue Jackets" = "CBJ",
  "Dallas Stars" = "DAL", "Detroit Red Wings" = "DET", "Edmonton Oilers" = "EDM",
  "Florida Panthers" = "FLA", "Los Angeles Kings" = "LAK", "Minnesota Wild" = "MIN",
  "Montreal Canadiens" = "MTL", "Montréal Canadiens" = "MTL", "Nashville Predators" = "NSH",
  "New Jersey Devils" = "NJD", "New York Islanders" = "NYI", "New York Rangers" = "NYR",
  "Ottawa Senators" = "OTT", "Philadelphia Flyers" = "PHI", "Pittsburgh Penguins" = "PIT",
  "San Jose Sharks" = "SJS", "Seattle Kraken" = "SEA", "St. Louis Blues" = "STL",
  "Tampa Bay Lightning" = "TBL", "Toronto Maple Leafs" = "TOR",
  "Utah Hockey Club" = "UTA", "Utah Mammoth" = "UTA",
  "Vancouver Canucks" = "VAN", "Vegas Golden Knights" = "VGK", "Washington Capitals" = "WSH",
  "Winnipeg Jets" = "WPG"
)

# NHL division and conference mapping
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

# Use API-provided per-game stats
team_ratings <- standings %>%
  mutate(
    GP = as.numeric(gamesPlayed),
    GF_PG = round(goalsForPerGame, 2),
    GA_PG = round(goalsAgainstPerGame, 2),
    DIFF_PG = round(goalsForPerGame - goalsAgainstPerGame, 2),
    TEAM_ABBREV = team_abbrevs[teamFullName]
  ) %>%
  filter(!is.na(GF_PG) & !is.na(GA_PG) & GP > 0)

cat("\nTeam Ratings Preview:\n")
print(head(team_ratings %>% arrange(desc(DIFF_PG)) %>% select(teamFullName, TEAM_ABBREV, GF_PG, GA_PG, DIFF_PG), 10))

# Convert to list format for JSON matching ScatterPlotVisualization model
# For NHL: x = Goals For per Game, y = Goals Against per Game
# invertYAxis = TRUE because lower goals against is better
data_points <- team_ratings %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = TEAM_ABBREV,
    x = GF_PG,
    y = GA_PG,
    sum = DIFF_PG,
    division = team_divisions[TEAM_ABBREV],
    conference = team_conferences[TEAM_ABBREV]
  ))) %>%
  pull(data_point)

# Create output object with metadata matching ScatterPlotVisualization model
output_data <- list(
  sport = "NHL",
  visualizationType = "SCATTER_PLOT",
  title = paste0("NHL Team Efficiency - ", nhl_season_start, "-", substr(nhl_season_end, 3, 4)),
  subtitle = "Goals For vs Goals Against per Game",
  description = "Goals For per Game measures offensive output while Goals Against per Game measures defensive performance (lower is better). Teams in the top-right quadrant have elite offenses and defenses, making them Stanley Cup contenders. Goal differential per game is the best single measure of team quality.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "NHL Stats API",
  xAxisLabel = "Goals For / Game",
  yAxisLabel = "Goals Against / Game",
  xColumnLabel = "GF/G",
  yColumnLabel = "GA/G",
  invertYAxis = TRUE,
  quadrantTopRight = list(color = "#4CAF50", label = "Elite"),
  quadrantTopLeft = list(color = "#2196F3", label = "Defense First"),
  quadrantBottomLeft = list(color = "#F44336", label = "Struggling"),
  quadrantBottomRight = list(color = "#FFEB3B", label = "Offense First"),
  subject = "TEAM",
  dataPoints = data_points
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "nhl__team_efficiency.json"
} else {
  "dev/nhl__team_efficiency.json"
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
cat("\nTop 5 by Goal Differential/Game:\n")
print(head(team_ratings %>% arrange(desc(DIFF_PG)) %>% select(TEAM_ABBREV, GF_PG, GA_PG, DIFF_PG), 5))
cat("\nBottom 5 by Goal Differential/Game:\n")
print(tail(team_ratings %>% arrange(desc(DIFF_PG)) %>% select(TEAM_ABBREV, GF_PG, GA_PG, DIFF_PG), 5))
