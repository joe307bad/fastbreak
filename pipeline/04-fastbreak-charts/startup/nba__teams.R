library(hoopR)
library(dplyr)
library(jsonlite)

cat("Generating NBA team roster\n")

# NBA division, conference mapping, and theme colors
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
  # Theme colors: primary and secondary for light and dark modes
  lightPrimary = c(
    "#E03A3E", "#007A33", "#000000", "#1D1160", "#CE1141", "#860038",
    "#00538C", "#0E2240", "#C8102E", "#1D428A", "#CE1141", "#002D62",
    "#C8102E", "#552583", "#5D76A9", "#98002E", "#00471B", "#0C2340",
    "#0C2340", "#006BB6", "#007AC1", "#0077C0", "#006BB6", "#1D1160",
    "#E03A3E", "#5A2D81", "#C4CED4", "#CE1141", "#002B5C", "#002B5C"
  ),
  lightSecondary = c(
    "#C1D32F", "#BA9653", "#FFFFFF", "#00788C", "#000000", "#FFB81C",
    "#002B5E", "#FEC524", "#1D42BA", "#FFC72C", "#000000", "#FDBA21",
    "#1D428A", "#FDB927", "#12173F", "#F9A01B", "#EEE1C6", "#236192",
    "#C8102E", "#F58426", "#EF3B24", "#C4CED4", "#ED174C", "#E56020",
    "#000000", "#63727A", "#000000", "#000000", "#F9A01B", "#E31837"
  ),
  darkPrimary = c(
    "#E03A3E", "#007A33", "#FFFFFF", "#00788C", "#CE1141", "#FFB81C",
    "#00538C", "#FEC524", "#1D42BA", "#FFC72C", "#CE1141", "#FDBA21",
    "#1D428A", "#FDB927", "#5D76A9", "#F9A01B", "#EEE1C6", "#78BE20",
    "#B4975A", "#F58426", "#EF3B24", "#000000", "#ED174C", "#E56020",
    "#E03A3E", "#5A2D81", "#C4CED4", "#CE1141", "#F9A01B", "#E31837"
  ),
  darkSecondary = c(
    "#C1D32F", "#BA9653", "#707070", "#A1A1A4", "#FFFFFF", "#041E42",
    "#B8C4CA", "#8B2131", "#C8102E", "#1D428A", "#C4CED4", "#002D62",
    "#C8102E", "#552583", "#F5B112", "#000000", "#00471B", "#0C2340",
    "#0C2340", "#006BB6", "#002D62", "#0077C0", "#002B5C", "#1D1160",
    "#FFFFFF", "#63727A", "#000000", "#A1A1A4", "#00471B", "#002B5C"
  ),
  stringsAsFactors = FALSE
)

cat("Loaded", nrow(team_data), "NBA teams\n")

# Create team roster with searchable labels and colors
team_roster <- team_data %>%
  mutate(longLabel = paste0("NBA - ", team_name)) %>%
  select(code, longLabel, conference, division,
         lightPrimary, lightSecondary, darkPrimary, darkSecondary) %>%
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

env <- toupper(Sys.getenv("ENV", "DEV"))

s3_key <- if (env == "PROD") {
  "prod/teams/nba__teams.json"
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
