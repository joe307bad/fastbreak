library(nflreadr)
library(dplyr)
library(jsonlite)

cat("Generating NFL team roster\n")

# Load NFL teams data
teams <- nflreadr::load_teams() %>%
  filter(!is.na(team_abbr)) %>%
  select(team_abbr, team_name, team_conf, team_division)

cat("Loaded", nrow(teams), "NFL teams\n")

# Create team roster with searchable labels
team_roster <- teams %>%
  mutate(
    code = team_abbr,
    longLabel = paste0("NFL - ", team_name),
    conference = team_conf,
    division = team_division
  ) %>%
  select(code, longLabel, conference, division) %>%
  arrange(code)

cat("\nTeam Roster Preview:\n")
print(head(team_roster, 5))

# Create output object
output_data <- list(
  sport = "NFL",
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
  "teams/nfl__teams.json"
} else {
  "dev/teams/nfl__teams.json"
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
