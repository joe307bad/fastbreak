library(dplyr)
library(jsonlite)

cat("Generating MLB team roster\n")

# MLB team data with divisions, conferences (leagues), and theme colors
team_data <- data.frame(
  code = c(
    "ARI", "ATL", "BAL", "BOS", "CHC", "CHW", "CIN", "CLE", "COL",
    "DET", "HOU", "KCR", "LAA", "LAD", "MIA", "MIL", "MIN", "NYM",
    "NYY", "OAK", "PHI", "PIT", "SDP", "SEA", "SFG", "STL", "TBR",
    "TEX", "TOR", "WSN"
  ),
  team_name = c(
    "Arizona Diamondbacks", "Atlanta Braves", "Baltimore Orioles", "Boston Red Sox",
    "Chicago Cubs", "Chicago White Sox", "Cincinnati Reds", "Cleveland Guardians",
    "Colorado Rockies", "Detroit Tigers", "Houston Astros", "Kansas City Royals",
    "Los Angeles Angels", "Los Angeles Dodgers", "Miami Marlins", "Milwaukee Brewers",
    "Minnesota Twins", "New York Mets", "New York Yankees", "Oakland Athletics",
    "Philadelphia Phillies", "Pittsburgh Pirates", "San Diego Padres", "Seattle Mariners",
    "San Francisco Giants", "St. Louis Cardinals", "Tampa Bay Rays", "Texas Rangers",
    "Toronto Blue Jays", "Washington Nationals"
  ),
  division = c(
    "NL West", "NL East", "AL East", "AL East", "NL Central", "AL Central",
    "NL Central", "AL Central", "NL West", "AL Central", "AL West", "AL Central",
    "AL West", "NL West", "NL East", "NL Central", "AL Central", "NL East",
    "AL East", "AL West", "NL East", "NL Central", "NL West", "AL West",
    "NL West", "NL Central", "AL East", "AL West", "AL East", "NL East"
  ),
  conference = c(
    "National League", "National League", "American League", "American League",
    "National League", "American League", "National League", "American League",
    "National League", "American League", "American League", "American League",
    "American League", "National League", "National League", "National League",
    "American League", "National League", "American League", "American League",
    "National League", "National League", "National League", "American League",
    "National League", "National League", "American League", "American League",
    "American League", "National League"
  ),
  # Theme colors: primary and secondary for light and dark modes
  lightPrimary = c(
    "#A71930", "#CE1141", "#DF4601", "#BD3039", "#0E3386", "#27251F", "#C6011F", "#0C2340", "#33006F",
    "#0C2340", "#002D62", "#004687", "#BA0021", "#005A9C", "#00A3E0", "#12284B", "#002B5C", "#002D72",
    "#003087", "#003831", "#E81828", "#FDB827", "#2F241D", "#0C2C56", "#FD5A1E", "#C41E3A", "#092C5C",
    "#003278", "#134A8E", "#AB0003"
  ),
  lightSecondary = c(
    "#E3D4AD", "#13274F", "#000000", "#0C2340", "#CC3433", "#C4CED4", "#000000", "#E31937", "#C4CED4",
    "#FA4616", "#EB6E1F", "#BD9B60", "#003263", "#EF3E42", "#EF3340", "#B6922E", "#D31145", "#FF5910",
    "#0C2340", "#EFB21E", "#002D72", "#27251F", "#FFC425", "#005C5C", "#27251F", "#0C2340", "#8FBCE6",
    "#C0111F", "#1D2D5C", "#14225A"
  ),
  darkPrimary = c(
    "#000000", "#CE1141", "#DF4601", "#BD3039", "#CC3433", "#C4CED4", "#C6011F", "#E31937", "#33006F",
    "#FA4616", "#EB6E1F", "#BD9B60", "#BA0021", "#005A9C", "#000000", "#B6922E", "#D31145", "#FF5910",
    "#003087", "#EFB21E", "#E81828", "#FDB827", "#FFC425", "#005C5C", "#FD5A1E", "#C41E3A", "#8FBCE6",
    "#C0111F", "#134A8E", "#AB0003"
  ),
  darkSecondary = c(
    "#A71930", "#13274F", "#000000", "#0C2340", "#0E3386", "#27251F", "#000000", "#0C2340", "#C4CED4",
    "#0C2340", "#002D62", "#004687", "#003263", "#FFFFFF", "#00A3E0", "#12284B", "#002B5C", "#002D72",
    "#FFFFFF", "#003831", "#002D72", "#27251F", "#2F241D", "#C4CED4", "#27251F", "#FEDB00", "#F5D130",
    "#003278", "#E8291C", "#14225A"
  ),
  stringsAsFactors = FALSE
)

cat("Loaded", nrow(team_data), "MLB teams\n")

# Create team roster with searchable labels and colors
team_roster <- team_data %>%
  mutate(longLabel = paste0("MLB - ", team_name)) %>%
  select(code, longLabel, conference, division,
         lightPrimary, lightSecondary, darkPrimary, darkSecondary) %>%
  arrange(code)

cat("\nTeam Roster Preview:\n")
print(head(team_roster, 5))

# Create output object
output_data <- list(
  sport = "MLB",
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
  "prod/teams/mlb__teams.json"
} else {
  "dev/teams/mlb__teams.json"
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
