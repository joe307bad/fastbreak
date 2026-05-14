library(dplyr)
library(jsonlite)

cat("Generating NHL team roster\n")

# NHL team data with divisions, conferences, and theme colors
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
  # Theme colors: primary and secondary for light and dark modes
  lightPrimary = c(
    "#F47A38", "#FFB81C", "#002654", "#CC0000", "#002654", "#D2001C",
    "#CF0A2C", "#6F263D", "#006847", "#CE1126", "#FF4C00", "#041E42",
    "#111111", "#154734", "#AF1E2D", "#CE1126", "#FFB81C", "#00539B",
    "#0038A8", "#E31837", "#F74902", "#FFB81C", "#001628", "#006D75",
    "#002F87", "#002868", "#00205B", "#69B3E7", "#00205B", "#B4975A",
    "#041E42", "#C8102E"
  ),
  lightSecondary = c(
    "#B9975B", "#000000", "#FFB81C", "#000000", "#CE1126", "#F1BE48",
    "#000000", "#236192", "#8F8F8C", "#FFFFFF", "#041E42", "#C8102E",
    "#A2AAAD", "#A6192E", "#192168", "#000000", "#041E42", "#F47D30",
    "#CE1126", "#C69214", "#000000", "#000000", "#99D9D9", "#EA7200",
    "#FCB514", "#FFFFFF", "#FFFFFF", "#010101", "#00843D", "#333F42",
    "#004C97", "#041E42"
  ),
  darkPrimary = c(
    "#F47A38", "#FFB81C", "#FFB81C", "#CC0000", "#CE1126", "#F1BE48",
    "#CF0A2C", "#236192", "#006847", "#CE1126", "#FF4C00", "#B9975B",
    "#A2AAAD", "#DDCBA4", "#AF1E2D", "#CE1126", "#FFB81C", "#F47D30",
    "#CE1126", "#C69214", "#F74902", "#FFB81C", "#99D9D9", "#006D75",
    "#FCB514", "#002868", "#00205B", "#69B3E7", "#00843D", "#B4975A",
    "#004C97", "#C8102E"
  ),
  darkSecondary = c(
    "#000000", "#000000", "#002654", "#A2AAAD", "#002654", "#C8102E",
    "#FF671B", "#6F263D", "#111111", "#FFFFFF", "#041E42", "#041E42",
    "#111111", "#154734", "#192168", "#000000", "#041E42", "#00539B",
    "#0038A8", "#000000", "#000000", "#000000", "#355464", "#000000",
    "#002F87", "#FFFFFF", "#FFFFFF", "#010101", "#00205B", "#000000",
    "#AC162C", "#041E42"
  ),
  stringsAsFactors = FALSE
)

cat("Loaded", nrow(team_data), "NHL teams\n")

# Create team roster with searchable labels and colors
team_roster <- team_data %>%
  mutate(longLabel = paste0("NHL - ", team_name)) %>%
  select(code, longLabel, conference, division,
         lightPrimary, lightSecondary, darkPrimary, darkSecondary) %>%
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
