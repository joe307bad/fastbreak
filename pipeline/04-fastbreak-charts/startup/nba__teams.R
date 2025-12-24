library(hoopR)
library(dplyr)
library(jsonlite)

cat("Generating NBA team roster\n")

# NBA division and conference mapping
team_data <- data.frame(
  code = c(
    "ATL", "BOS", "BKN", "CHA", "CHI", "CLE", "DAL", "DEN", "DET",
    "GSW", "HOU", "IND", "LAC", "LAL", "MEM", "MIA", "MIL", "MIN",
    "NOP", "NYK", "OKC", "ORL", "PHI", "PHX", "POR", "SAC", "SAS",
    "TOR", "UTA", "WAS"
  ),
  team_name = c(
    "Atlanta Hawks", "Boston Celtics", "Brooklyn Nets", "Charlotte Hornets",
    "Chicago Bulls", "Cleveland Cavaliers", "Dallas Mavericks", "Denver Nuggets",
    "Detroit Pistons", "Golden State Warriors", "Houston Rockets", "Indiana Pacers",
    "LA Clippers", "Los Angeles Lakers", "Memphis Grizzlies", "Miami Heat",
    "Milwaukee Bucks", "Minnesota Timberwolves", "New Orleans Pelicans",
    "New York Knicks", "Oklahoma City Thunder", "Orlando Magic", "Philadelphia 76ers",
    "Phoenix Suns", "Portland Trail Blazers", "Sacramento Kings", "San Antonio Spurs",
    "Toronto Raptors", "Utah Jazz", "Washington Wizards"
  ),
  division = c(
    "Southeast", "Atlantic", "Atlantic", "Southeast", "Central", "Central",
    "Southwest", "Northwest", "Central", "Pacific", "Southwest", "Central",
    "Pacific", "Pacific", "Southwest", "Southeast", "Central", "Northwest",
    "Southwest", "Atlantic", "Northwest", "Southeast", "Atlantic", "Pacific",
    "Northwest", "Pacific", "Southwest", "Atlantic", "Northwest", "Southeast"
  ),
  conference = c(
    "Eastern", "Eastern", "Eastern", "Eastern", "Eastern", "Eastern",
    "Western", "Western", "Eastern", "Western", "Western", "Eastern",
    "Western", "Western", "Western", "Eastern", "Eastern", "Western",
    "Western", "Eastern", "Western", "Eastern", "Eastern", "Western",
    "Western", "Western", "Western", "Eastern", "Western", "Eastern"
  ),
  stringsAsFactors = FALSE
)

cat("Loaded", nrow(team_data), "NBA teams\n")

# Create team roster with searchable labels
team_roster <- team_data %>%
  mutate(longLabel = paste0("NBA - ", team_name)) %>%
  select(code, longLabel, conference, division) %>%
  arrange(code)

cat("\nTeam Roster Preview:\n")
print(head(team_roster, 5))

# Create output object
output_data <- list(
  sport = "NBA",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  teams = team_roster
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "teams/nba__teams.json"
} else {
  "dev/teams/nba__teams.json"
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
cat("Total teams:", nrow(team_roster), "\n")
