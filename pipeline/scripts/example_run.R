# Example script to demonstrate the data collection pipeline
# This script shows how to collect a week's worth of game data

# Load the pipeline
source("config/setup.R")
source("scripts/collect_game_data.R")

# Example 1: Collect data for first week of April 2024
cat("=== Example 1: First week of April 2024 ===\n")
april_week1 <- collect_game_data(
  start_date = "2024-04-01",
  end_date = "2024-04-07", 
  output_file = "april_week1_games.csv"
)

cat("Sample of collected data:\n")
print(head(april_week1, 3))

# Example 2: Collect data for a single day
cat("\n=== Example 2: Single day (2024-04-01) ===\n")
single_day <- collect_game_data(
  start_date = "2024-04-01",
  end_date = "2024-04-01",
  output_file = "single_day_games.csv"
)

cat("Games on 2024-04-01:\n")
print(single_day)

# Example 3: Test individual functions
cat("\n=== Example 3: Testing individual functions ===\n")

# Test weather data generation
cat("Testing weather data for LAD on 2024-04-01:\n")
weather_test <- get_weather_data("LAD", "2024-04-01")
print(weather_test)

# Example 4: Check data quality
cat("\n=== Example 4: Data quality check ===\n")

check_data_quality <- function(df) {
  cat("Data quality summary:\n")
  cat("Total rows:", nrow(df), "\n")
  cat("Columns with missing data:\n")
  
  missing_counts <- df %>%
    summarise(across(everything(), ~sum(is.na(.x)))) %>%
    pivot_longer(everything(), names_to = "column", values_to = "missing_count") %>%
    filter(missing_count > 0) %>%
    arrange(desc(missing_count))
  
  if (nrow(missing_counts) > 0) {
    print(missing_counts)
  } else {
    cat("No missing data found!\n")
  }
  
  cat("\nTemperature range:", min(df$Temperature, na.rm = TRUE), "to", max(df$Temperature, na.rm = TRUE), "°F\n")
  cat("Wind speed range:", min(df$WindSpeed, na.rm = TRUE), "to", max(df$WindSpeed, na.rm = TRUE), "mph\n")
}

check_data_quality(april_week1)

cat("\n=== Pipeline demonstration complete! ===\n")
cat("Check the 'output/' folder for generated CSV files.\n")