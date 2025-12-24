library(dplyr)
library(jsonlite)

cat("Generating NHL team roster\n")

# NHL team data with divisions and conferences
team_data <- data.frame(
  code = c(
    "ANA", "BOS", "BUF", "CAR", "CBJ", "CGY", "CHI", "COL", "DAL",
    "DET", "EDM", "FLA", "LAK", "MIN", "MTL", "NJD", "NSH", "NYI",
    "NYR", "OTT", "PHI", "PIT", "SEA", "SJS", "STL", "TBL", "TOR",
    "UTA", "VAN", "VGK", "WPG", "WSH"
  ),
  team_name = c(
    "Anaheim Ducks", "Boston Bruins", "Buffalo Sabres", "Carolina Hurricanes",
    "Columbus Blue Jackets", "Calgary Flames", "Chicago Blackhawks", "Colorado Avalanche",
    "Dallas Stars", "Detroit Red Wings", "Edmonton Oilers", "Florida Panthers",
    "Los Angeles Kings", "Minnesota Wild", "Montreal Canadiens", "New Jersey Devils",
    "Nashville Predators", "New York Islanders", "New York Rangers", "Ottawa Senators",
    "Philadelphia Flyers", "Pittsburgh Penguins", "Seattle Kraken", "San Jose Sharks",
    "St. Louis Blues", "Tampa Bay Lightning", "Toronto Maple Leafs", "Utah Hockey Club",
    "Vancouver Canucks", "Vegas Golden Knights", "Winnipeg Jets", "Washington Capitals"
  ),
  division = c(
    "Pacific", "Atlantic", "Atlantic", "Metropolitan", "Metropolitan", "Pacific",
    "Central", "Central", "Central", "Atlantic", "Pacific", "Atlantic",
    "Pacific", "Central", "Atlantic", "Metropolitan", "Central", "Metropolitan",
    "Metropolitan", "Atlantic", "Metropolitan", "Metropolitan", "Pacific", "Pacific",
    "Central", "Atlantic", "Atlantic", "Central", "Pacific", "Pacific",
    "Central", "Metropolitan"
  ),
  conference = c(
    "Western", "Eastern", "Eastern", "Eastern", "Eastern", "Western",
    "Western", "Western", "Western", "Eastern", "Western", "Eastern",
    "Western", "Western", "Eastern", "Eastern", "Western", "Eastern",
    "Eastern", "Eastern", "Eastern", "Eastern", "Western", "Western",
    "Western", "Eastern", "Eastern", "Western", "Western", "Western",
    "Western", "Eastern"
  ),
  stringsAsFactors = FALSE
)

cat("Loaded", nrow(team_data), "NHL teams\n")

# Create team roster with searchable labels
team_roster <- team_data %>%
  mutate(longLabel = paste0("NHL - ", team_name)) %>%
  select(code, longLabel, conference, division) %>%
  arrange(code)

cat("\nTeam Roster Preview:\n")
print(head(team_roster, 5))

# Create output object
output_data <- list(
  sport = "NHL",
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
  "teams/nhl__teams.json"
} else {
  "dev/teams/nhl__teams.json"
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
