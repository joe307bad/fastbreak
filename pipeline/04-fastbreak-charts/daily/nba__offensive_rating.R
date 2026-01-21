library(hoopR)
library(dplyr)
library(jsonlite)

# Load NBA utilities
# Get script directory - works with both source() and Rscript
args <- commandArgs(trailingOnly = FALSE)
file_arg <- grep("^--file=", args, value = TRUE)
if (length(file_arg) > 0) {
  script_dir <- dirname(sub("^--file=", "", file_arg))
} else {
  script_dir <- getwd()
}
source(file.path(script_dir, "..", "utils", "nba_utils.R"))

# Get current NBA season
nba_season <- get_current_nba_season()

cat("Processing NBA Offensive Rating for", nba_season - 1, "-", nba_season, "season\n")

# Load team stats using hoopR
team_stats <- tryCatch({
  hoopR::nba_leaguedashteamstats(
    season = format_nba_season(nba_season),
    measure_type = "Advanced",
    per_mode = "PerGame"
  )$LeagueDashTeamStats
}, error = function(e) {
  cat("Error loading team stats:", e$message, "\n")
  stop(e)
})

cat("Loaded advanced stats for", nrow(team_stats), "teams\n")
cat("Available columns:", paste(names(team_stats), collapse = ", "), "\n\n")

# The team stats data structure uses TEAM_NAME, not TEAM_ABBREVIATION
# We need to create a mapping from team names to abbreviations
team_name_to_abbr <- c(
  "Atlanta Hawks" = "ATL", "Boston Celtics" = "BOS", "Brooklyn Nets" = "BKN",
  "Charlotte Hornets" = "CHA", "Chicago Bulls" = "CHI", "Cleveland Cavaliers" = "CLE",
  "Dallas Mavericks" = "DAL", "Denver Nuggets" = "DEN", "Detroit Pistons" = "DET",
  "Golden State Warriors" = "GSW", "Houston Rockets" = "HOU", "Indiana Pacers" = "IND",
  "LA Clippers" = "LAC", "Los Angeles Lakers" = "LAL", "Memphis Grizzlies" = "MEM",
  "Miami Heat" = "MIA", "Milwaukee Bucks" = "MIL", "Minnesota Timberwolves" = "MIN",
  "New Orleans Pelicans" = "NOP", "New York Knicks" = "NYK", "Oklahoma City Thunder" = "OKC",
  "Orlando Magic" = "ORL", "Philadelphia 76ers" = "PHI", "Phoenix Suns" = "PHX",
  "Portland Trail Blazers" = "POR", "Sacramento Kings" = "SAC", "San Antonio Spurs" = "SAS",
  "Toronto Raptors" = "TOR", "Utah Jazz" = "UTA", "Washington Wizards" = "WAS"
)

# Extract offensive rating (points per 100 possessions) and team info
offensive_ratings <- team_stats %>%
  select(TEAM_NAME, OFF_RATING) %>%
  mutate(
    team_abbr = team_name_to_abbr[TEAM_NAME],
    offensive_rating = as.numeric(OFF_RATING),
    division = nba_team_divisions[team_abbr],
    conference = nba_team_conferences[team_abbr]
  ) %>%
  filter(!is.na(team_abbr) & team_abbr != "" & !is.na(offensive_rating)) %>%
  arrange(desc(offensive_rating)) %>%
  select(team = team_abbr, offensive_rating, division, conference)

cat("Teams processed:", nrow(offensive_ratings), "\n")

# Convert to list format for JSON matching BarGraphVisualization model
# BarGraphDataPoint: label, value, division, conference
data_points <- offensive_ratings %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = team,
    value = as.numeric(offensive_rating),
    division = if (!is.na(division) && !is.null(division)) as.character(division) else NULL,
    conference = if (!is.na(conference) && !is.null(conference)) as.character(conference) else NULL
  ))) %>%
  pull(data_point)

# Create output object with metadata matching BarGraphVisualization model
output_data <- list(
  sport = "NBA",
  visualizationType = "BAR_GRAPH",
  title = paste0("NBA Offensive Rating - ", nba_season - 1, "-", substr(nba_season, 3, 4)),
  subtitle = "Points Per 100 Possessions",
  description = "Offensive Rating measures a team's points scored per 100 possessions. This advanced metric accounts for pace of play, making it a better indicator of offensive efficiency than raw points per game. Higher values indicate more efficient offenses that generate more points per possession. Elite offenses typically rate above 115, while struggling offenses fall below 110.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "hoopR / NBA Stats",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  sortOrder = 2,
  dataPoints = data_points,
  topReferenceLine = list(
    value = 115,
    label = "Elite Offense",
    color = "#4CAF50"
  ),
  bottomReferenceLine = list(
    value = 110,
    label = "Struggling Offense",
    color = "#F44336"
  )
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "nba__offensive_rating.json"
} else {
  "dev/nba__offensive_rating.json"
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
cat("\nOffensive Rating Summary:\n")
cat("Top 5 teams:\n")
print(head(offensive_ratings %>% select(team, offensive_rating), 5))
cat("\nBottom 5 teams:\n")
print(tail(offensive_ratings %>% select(team, offensive_rating), 5))
cat("\nTotal teams:", length(data_points), "\n")
