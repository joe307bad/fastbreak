library(nflfastR)
library(dplyr)
library(jsonlite)

# Get current season and most recent week
current_season <- as.numeric(format(Sys.Date(), "%Y"))
pbp <- nflfastR::load_pbp(current_season)

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

# Write JSON output
output_dir <- if (dir.exists("/app/output")) {
  "/app/output"
} else if (dir.exists("../../../server/nginx/static")) {
  "../../../server/nginx/static"
} else {
  "output"
}

# Create output directory if it doesn't exist
if (!dir.exists(output_dir)) {
  dir.create(output_dir, recursive = TRUE)
}

output_file <- file.path(output_dir, paste0("nfl__team_tier_list.json"))
write_json(output_data, output_file, pretty = TRUE, auto_unbox = TRUE)

cat("NFL team EPA data generated:", output_file, "\n")
cat("Total teams:", length(team_list), "\n")
