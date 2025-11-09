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

# Write JSON output
output_dir <- if (dir.exists("/app/output")) "/app/output" else "output"
output_file <- file.path(output_dir, paste0("nfl__team_tier_list__", most_recent_week, ".json"))
write_json(team_list, output_file, pretty = TRUE, auto_unbox = TRUE)

cat("NFL team EPA data generated:", output_file, "\n")
cat("Total teams:", length(team_list), "\n")
