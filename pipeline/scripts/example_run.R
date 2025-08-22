# Script to collect data for a single game
# Load the pipeline
source("config/setup.R")
source("scripts/collect_game_data.R")

# Collect data for a single day
cat("=== Single day (2024-04-01) ===\n")
single_day <- collect_game_data(
  start_date = "2024-04-01",
  end_date = "2024-04-01",
  output_file = "single_day_games.csv"
)

cat("Game processed:\n")
print(single_day)