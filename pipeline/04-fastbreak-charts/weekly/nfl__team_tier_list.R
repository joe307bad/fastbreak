library(nflreadr)
library(dplyr)
library(jsonlite)

# Get current season and most recent week
current_season <- as.numeric(format(Sys.Date(), "%Y"))
pbp <- nflreadr::load_pbp(current_season)

# Get the most recent week with data
most_recent_week <- max(pbp$week, na.rm = TRUE)

cat("Processing NFL data for season:", current_season, "week:", most_recent_week, "\n")

# Calculate offensive EPA per play by team
offense_epa <- pbp %>%
  filter(week <= most_recent_week, !is.na(epa), !is.na(posteam)) %>%
  group_by(posteam) %>%
  summarise(offense_epa_per_play = mean(epa, na.rm = TRUE), .groups = "drop") %>%
  rename(team = posteam)

# Calculate defensive EPA per play by team
defense_epa <- pbp %>%
  filter(week <= most_recent_week, !is.na(epa), !is.na(defteam)) %>%
  group_by(defteam) %>%
  summarise(defense_epa_per_play = mean(epa, na.rm = TRUE), .groups = "drop") %>%
  rename(team = defteam)

# Combine offense and defense EPA
team_epa <- offense_epa %>%
  left_join(defense_epa, by = "team") %>%
  arrange(team)

# Convert to list format for JSON
team_list <- team_epa %>%
  rowwise() %>%
  mutate(team_data = list(list(
    team = team,
    offense_epa_per_play = round(offense_epa_per_play, 4),
    defense_epa_per_play = round(defense_epa_per_play, 4)
  ))) %>%
  pull(team_data)

# Create output object with metadata
output_data <- list(
  title = paste("NFL Team Tier List - Week", most_recent_week),
  description = "Expected Points Added (EPA) measures the value of each play by comparing the expected points before and after the play. Offensive EPA per play shows how many points a team adds per offensive play on average, while defensive EPA per play (where lower is better) shows how many points a team allows per defensive play. Teams in the top-right quadrant have strong offenses and defenses, making them the most dominant teams.",
  xAxisLabel = "Offensive EPA per Play",
  yAxisLabel = "Defensive EPA per Play",
  data = team_list
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "nfl__team_tier_list.json"
} else {
  "dev/nfl__team_tier_list.json"
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
cat("Total teams:", length(team_list), "\n")
