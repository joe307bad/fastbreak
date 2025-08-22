# Baseball Game Data Collection Pipeline

A comprehensive R-based pipeline for collecting baseball game data including starting pitchers, advanced team statistics, and weather conditions.

## Features

- **Starting Pitcher Detection**: Automatically identifies home and away starting pitchers using play-by-play data
- **Advanced Team Statistics**: Retrieves wOBA, ERA-, and FIP from FanGraphs
- **Individual Pitcher Stats**: ERA, WHIP, strikeouts, walks, innings pitched
- **Weather Data**: Temperature, wind speed, direction, precipitation
- **Game Outcomes**: Scores, team names, dates

## Quick Start

```r
# Run data collection for a single date
Rscript scripts/collect_daily_games.R

# Or customize the date
# Edit DEFAULT_DATE in scripts/collect_daily_games.R
```

## Configuration

### Environment Variables

- `LIMIT_GAMES=TRUE`: Process only the first game (for testing)
- `LOG_LEVEL`: Set to DEBUG, INFO, WARN, or ERROR (default: INFO)

### Example

```bash
# Collect all games with verbose logging
LOG_LEVEL=DEBUG Rscript scripts/collect_daily_games.R

# Test with first game only
LIMIT_GAMES=TRUE Rscript scripts/collect_daily_games.R
```

## Output

Data is saved to `output/game_data.csv` with the following columns:

### Game Information
- `GameId`: Unique game identifier
- `Date`: Game date
- `HomeTeam`, `AwayTeam`: Team names
- `HomeScore`, `AwayScore`: Final scores

### Pitcher Statistics
- `HomePitcherName`, `AwayPitcherName`: Starting pitcher names
- `HomePitcherERA`, `AwayPitcherERA`: Earned run averages
- `HomePitcherWHIP`, `AwayPitcherWHIP`: Walks + hits per inning
- `HomePitcherK`, `AwayPitcherK`: Strikeouts
- `HomePitcherBB`, `AwayPitcherBB`: Walks
- `HomePitcherIP`, `AwayPitcherIP`: Innings pitched

### Team Statistics
- `HomeOPS`, `AwayOPS`: On-base plus slugging
- `HomeWOBA`, `AwayWOBA`: Weighted on-base average
- `HomeERAPlus`, `AwayERAPlus`: ERA- (lower is better, 100 = average)
- `HomeFIP`, `AwayFIP`: Fielding independent pitching

### Weather
- `Temperature`: Temperature in Fahrenheit
- `WindSpeed`: Wind speed in mph
- `WindDirection`: Wind direction
- `Precipitation`: Precipitation amount

## Data Sources

- **Game Data**: MLB.com via baseballr package
- **Advanced Team Stats**: FanGraphs via baseballr package
- **Weather Data**: Custom weather API integration

## Dependencies

```r
# Required packages (installed automatically)
- tidyverse
- baseballr
- jsonlite
- lubridate
```

## File Structure

```
pipeline/
├── scripts/
│   ├── collect_daily_games.R    # Main execution script
│   └── collect_game_data.R      # Data collection functions
├── utils/
│   ├── data_utils.R             # Data processing utilities
│   └── weather_utils.R          # Weather data functions
├── config/
│   ├── setup.R                  # Package loading
│   └── config.R                 # Configuration constants
└── output/                      # Generated CSV files
```

## Error Handling

The pipeline includes comprehensive error handling:

- **Pitcher Detection**: Fails immediately if starting pitchers cannot be found
- **Data Validation**: Validates date formats and data availability
- **Graceful Degradation**: Advanced stats fallback to NA if unavailable
- **Logging**: Configurable logging levels for debugging

## Troubleshooting

### Common Issues

1. **"Could not find starting pitchers"**
   - Ensure the game has been played (not scheduled)
   - Check that play-by-play data is available

2. **"No FanGraphs data available"**
   - Verify internet connection
   - Check if FanGraphs API is accessible

3. **"No games found"**
   - Verify date format (YYYY-MM-DD)
   - Ensure games were scheduled for that date

### Debug Mode

```bash
LOG_LEVEL=DEBUG Rscript scripts/collect_daily_games.R
```

This provides detailed logging of all data collection steps.