# Fastbreak Baseball Data Pipeline

This R project collects and processes baseball game data to create comprehensive datasets for the Fastbreak Elo+ rating system.

## Setup

1. Install required packages:
```r
install.packages(c("baseballr", "tidyverse", "lubridate", "httr", "jsonlite"))
```

2. Open `pipeline.Rproj` in RStudio

## Usage

Run the main data collection script:
```r
source("scripts/collect_game_data.R")
```

## Output Format

The pipeline generates CSV files matching the structure of `../server/src/Fastbreak.Cli/test.csv` with columns:
- GameId, Date, HomeTeam, AwayTeam, HomeScore, AwayScore
- Temperature, WindSpeed, WindDirection, Precipitation  
- Pitcher stats (ERA, WHIP, K, BB, IP) for both teams
- Advanced team metrics (OPS, wOBA, ERA+, FIP)

## Scripts

- `collect_game_data.R` - Main data collection pipeline
- `utils/` - Helper functions for data processing
- `config/` - Configuration files
- `output/` - Generated CSV files