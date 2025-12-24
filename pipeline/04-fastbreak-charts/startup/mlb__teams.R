library(dplyr)
library(jsonlite)

cat("Generating MLB team roster\n")

# MLB team data with divisions and conferences (leagues)
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
  stringsAsFactors = FALSE
)

cat("Loaded", nrow(team_data), "MLB teams\n")

# Create team roster with searchable labels
team_roster <- team_data %>%
  mutate(longLabel = paste0("MLB - ", team_name)) %>%
  select(code, longLabel, conference, division) %>%
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

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "teams/mlb__teams.json"
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
