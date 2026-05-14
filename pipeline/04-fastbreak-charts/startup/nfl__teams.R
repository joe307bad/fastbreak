library(nflreadr)
library(dplyr)
library(jsonlite)

cat("Generating NFL team roster\n")

# Load NFL teams data
teams <- nflreadr::load_teams() %>%
  filter(!is.na(team_abbr)) %>%
  select(team_abbr, team_name, team_conf, team_division) %>%
  mutate(team_abbr = ifelse(team_abbr == "LA", "LAR", team_abbr))

cat("Loaded", nrow(teams), "NFL teams\n")

# Theme colors for NFL teams
team_colors <- data.frame(
  team_abbr = c(
    "ARI", "ATL", "BAL", "BUF", "CAR", "CHI", "CIN", "CLE", "DAL", "DEN",
    "DET", "GB", "HOU", "IND", "JAX", "KC", "LAC", "LAR", "LV", "MIA",
    "MIN", "NE", "NO", "NYG", "NYJ", "PHI", "PIT", "SEA", "SF", "TB",
    "TEN", "WAS"
  ),
  lightPrimary = c(
    "#97233F", "#A71930", "#241773", "#00338D", "#0085CA", "#0B162A",
    "#FB4F14", "#311D00", "#003594", "#FB4F14", "#0076B6", "#203731",
    "#03202F", "#002C5F", "#101820", "#E31837", "#0080C6", "#003594",
    "#000000", "#008E97", "#4F2683", "#002244", "#101820", "#0B2265",
    "#125740", "#004C54", "#FFB612", "#002244", "#AA0000", "#D50A0A",
    "#0C2340", "#5A1414"
  ),
  lightSecondary = c(
    "#000000", "#000000", "#000000", "#C60C30", "#101820", "#C83803",
    "#000000", "#FF3C00", "#041E42", "#002244", "#B0B7BC", "#FFB612",
    "#A71930", "#A2AAAD", "#D7A22A", "#FFB81C", "#FFC20E", "#FFA300",
    "#A5ACAF", "#F58220", "#FFC62F", "#C60C30", "#D3BC8D", "#A71930",
    "#000000", "#A5ACAF", "#101820", "#69BE28", "#B3995D", "#34302B",
    "#4B92DB", "#FFB612"
  ),
  darkPrimary = c(
    "#97233F", "#A71930", "#9E7C0C", "#C60C30", "#0085CA", "#C83803",
    "#FB4F14", "#FF3C00", "#869397", "#FB4F14", "#0076B6", "#FFB612",
    "#A71930", "#002C5F", "#006778", "#E31837", "#FFC20E", "#FFA300",
    "#A5ACAF", "#008E97", "#FFC62F", "#C60C30", "#D3BC8D", "#A71930",
    "#125740", "#004C54", "#FFB612", "#69BE28", "#B3995D", "#FF7900",
    "#4B92DB", "#FFB612"
  ),
  darkSecondary = c(
    "#FFB612", "#A5ACAF", "#241773", "#00338D", "#BFC0BF", "#0B162A",
    "#FFFFFF", "#311D00", "#003594", "#002244", "#B0B7BC", "#203731",
    "#03202F", "#A2AAAD", "#D7A22A", "#FFB81C", "#002A5E", "#003594",
    "#000000", "#F58220", "#4F2683", "#002244", "#101820", "#0B2265",
    "#FFFFFF", "#A5ACAF", "#101820", "#002244", "#AA0000", "#D50A0A",
    "#C8102E", "#5A1414"
  ),
  stringsAsFactors = FALSE
)

# Create team roster with searchable labels and colors
team_roster <- teams %>%
  left_join(team_colors, by = "team_abbr") %>%
  mutate(
    code = team_abbr,
    longLabel = paste0("NFL - ", team_name),
    conference = team_conf,
    division = team_division
  ) %>%
  select(code, longLabel, conference, division,
         lightPrimary, lightSecondary, darkPrimary, darkSecondary) %>%
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
