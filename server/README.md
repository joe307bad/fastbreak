# Fastbreak Server - baseballr Data Pipeline

This document outlines how to use the `baseballr` R package to create comprehensive baseball game datasets similar to our test.csv format.

## Required Data Points

Based on `test.csv`, we need to collect the following data for each game:

### Game Information
- `GameId` - Unique identifier (format: YYYY-MM-DD-AWAY-HOME)
- `Date` - Game date (YYYY-MM-DD)
- `HomeTeam` - Home team name
- `AwayTeam` - Away team name
- `HomeScore` - Final home team score
- `AwayScore` - Final away team score

### Weather Data
- `Temperature` - Game-time temperature (°F)
- `WindSpeed` - Wind speed (mph)
- `WindDirection` - Wind direction (N, S, E, W, NE, NW, SE, SW)
- `Precipitation` - Precipitation amount (inches)

### Pitcher Statistics
- `HomePitcherName` - Starting pitcher name
- `HomePitcherERA` - Pitcher's ERA
- `HomePitcherWHIP` - Pitcher's WHIP
- `HomePitcherK` - Pitcher's strikeouts
- `HomePitcherBB` - Pitcher's walks
- `HomePitcherIP` - Pitcher's innings pitched
- `AwayPitcherName` - (same stats for away pitcher)

### Team Advanced Statistics
- `HomeOPS` - Team OPS (On-base Plus Slugging)
- `AwayOPS` - Away team OPS
- `HomeWOBA` - Team weighted on-base average
- `AwayWOBA` - Away team wOBA
- `HomeERAPlus` - Team ERA+ (league adjusted)
- `AwayERAPlus` - Away team ERA+
- `HomeFIP` - Team Fielding Independent Pitching
- `AwayFIP` - Away team FIP

## baseballr Functions to Use

### Game Schedules & Results
```r
# Get game schedules and basic results
mlb_schedule(season = 2024, level_ids = 1)
mlb_game_info(game_pk = game_id)
mlb_game_linescore(game_pk = game_id)
```

### Weather Data
```r
# Weather data might need external APIs since baseballr doesn't include weather
# Consider using weatherAPI or similar service with stadium coordinates
```

### Pitcher Statistics (Point-in-Time)
```r
# Get pitcher stats as of specific date using Baseball Reference daily data
bref_daily_pitcher(t1 = "2024-04-01", t2 = "2024-04-01")

# Get pitcher stats through a specific date (season-to-date)
bref_daily_pitcher(t1 = "2024-03-28", t2 = game_date)

# This provides advanced pitcher metrics including:
# - ERA, WHIP, K, BB, IP (basic stats)
# - FIP (Fielding Independent Pitching)
# - ERA+ (park/league adjusted ERA)
# - K/9, BB/9, HR/9 rates
```

### Batter/Team Statistics (Point-in-Time)
```r
# Get team batting stats as of specific date
bref_daily_batter(t1 = "2024-03-28", t2 = game_date)

# This provides advanced batting metrics including:
# - OPS (On-base Plus Slugging)
# - wOBA (weighted On-Base Average)  
# - wRC+ (weighted Runs Created Plus)
# - ISO (Isolated Power)
# - BABIP (Batting Average on Balls in Play)

# Filter by team to get team-level aggregated stats
team_batting <- bref_daily_batter(t1 = "2024-03-28", t2 = game_date) %>%
  filter(Team == "LAD") %>%
  summarise(across(where(is.numeric), sum, na.rm = TRUE))
```

### Team Pitching Statistics (Point-in-Time)
```r
# Get team pitching stats as of specific date
team_pitching <- bref_daily_pitcher(t1 = "2024-03-28", t2 = game_date) %>%
  filter(Team == "LAD") %>%
  summarise(
    Team_ERA = weighted.mean(ERA, IP, na.rm = TRUE),
    Team_WHIP = weighted.mean(WHIP, IP, na.rm = TRUE),
    Team_FIP = weighted.mean(FIP, IP, na.rm = TRUE),
    Team_ERAPlus = weighted.mean(ERA_plus, IP, na.rm = TRUE)
  )
```

## Data Pipeline Workflow

1. **Fetch Game Schedule** - Get all games for desired date range
2. **Game Results** - Get final scores for each game
3. **Starting Pitchers** - Identify starting pitchers for each game
4. **Point-in-Time Pitcher Stats** - Use `bref_daily_pitcher()` to get pitcher stats as of game date
5. **Point-in-Time Team Stats** - Use `bref_daily_batter()` and `bref_daily_pitcher()` for team metrics
6. **Weather Data** - Integrate weather API data using stadium locations
7. **Export to CSV** - Format data to match test.csv structure

## Implementation Notes

- Use `bref_daily_pitcher()` and `bref_daily_batter()` for accurate point-in-time statistics
- Weather data will require a separate weather API service
- Baseball Reference functions provide most advanced metrics (ERA+, FIP, wOBA, OPS)
- Consider caching daily stats to avoid repeated API calls for same dates
- Stadium coordinates needed for weather data lookup
- Team stats can be calculated by aggregating individual player stats from the same team

## Next Steps

1. Set up R environment with baseballr package
2. Prototype data fetching for a single game
3. Implement weather data integration
4. Build complete pipeline for season data
5. Validate output format matches test.csv structure