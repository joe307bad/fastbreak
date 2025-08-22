# Test script for single game to verify pitcher fix works
source("config/setup.R")
source("scripts/collect_game_data.R")

cat("=== Testing Single Game Pitcher Extraction ===\n")

# Test with just one game
single_game <- collect_game_data(
  start_date = "2024-04-01",
  end_date = "2024-04-01", 
  output_file = "single_game_test.csv"
)

cat("Success! Single game processed:\n")
print(head(single_game, 1))