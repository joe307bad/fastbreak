# Fastbreak

A sports analytics platform with advanced Elo+ rating system for baseball analysis.

## Elo+ Rating System Brainstorm

### Overview
The Elo+ system is a two-pass machine learning approach that extends traditional Elo ratings by incorporating advanced baseball analytics and environmental factors.

### Architecture

#### Current CLI Structure
- **F# CLI Application** (`server/src/Fastbreak.Cli/`)
- **Current Commands**: ✅ `generate-elo-plus`, 🔲 `predict-game`, 🔲 `convert-retrosheet`
- **Framework**: Argu for command-line parsing
- **Target**: .NET 9.0

#### Implementation Status

##### 1. ✅ Remove Example Commands
- ✅ Remove existing `Export_Leaderboard` and `Export_Stats` commands
- ✅ Clean up DataExport.fs module (renamed to EloPlus.fs)

##### 2. ✅ New CLI Command: `generate-elo-plus`
```
fastbreak-cli generate-elo-plus [options]

Options:
  --file <path>           CSV file path for game data (optional, uses sample data if not specified)
  --progress <n>          Report progress every N lines (default: 10)
  --help                  Show help information
```

##### 2b. 🔲 New CLI Command: `predict-game`
```
fastbreak-cli predict-game [options]

Options:
  --json <data>           JSON game data for prediction (inline)
  --file <path>           JSON file path containing game data
  --ratings <path>        CSV file with current team ratings (optional, uses latest if not specified)
  --verbose               Include detailed prediction breakdown
  --help                  Show help information

Example JSON Format:
{
  "homeTeam": "Boston Red Sox",
  "awayTeam": "New York Yankees", 
  "weather": { "temperature": 72.0, "windSpeed": 8.5 },
  "homePitcher": { "name": "Chris Sale", "era": 2.85 },
  "awayPitcher": { "name": "Gerrit Cole", "era": 3.20 }
}

Output:
Prediction: New York Yankees (67.3% confidence)
Factors: Elo difference (+45), Pitcher advantage (+12), Weather neutral
```

##### 2c. 🔲 New CLI Command: `convert-retrosheet`
```
fastbreak-cli convert-retrosheet [options]

Options:
  --input <path>          Directory containing Retrosheet files (.EVE, .ROS)
  --output <path>         Output CSV file path (default: retrosheet-converted.csv)
  --season <year>         Process specific season (e.g., 2023)
  --seasons <start-end>   Process range of seasons (e.g., 2020-2023)
  --weather               Include weather data estimation (default: false)
  --verbose               Show detailed processing information
  --help                  Show help information

Example Usage:
fastbreak-cli convert-retrosheet --input ./retrosheet/2023 --output games-2023.csv --season 2023 --weather

Output Format: Matches test.csv structure with 28 columns
- Converts Retrosheet event data to standardized game results
- Extracts pitcher statistics and performance metrics
- Estimates SABR metrics from available play-by-play data
- Optional weather data integration from historical sources
```

##### 3. ✅ Game Data Entity
Simple entity structure to represent:
- ✅ Game results (win/loss)
- ✅ Team identifiers
- ✅ Weather data (temperature, wind, precipitation)
- ✅ Pitcher statistics
- ✅ Basic SABR metrics (OPS, ERA+, FIP, etc.)

##### 4. ✅ Two-Pass Elo Calculation

**✅ First Pass: Traditional Elo**
- ✅ Input: Game results (wins/losses only)
- ✅ Output: Standard Elo ratings
- ✅ Uses classic Elo algorithm with K-factor adjustment
- ✅ Stores initial ratings for reference

**✅ Second Pass: Elo+ Enhancement**
- ✅ Input: First pass Elo ratings + enhanced features
- ✅ Features: Weather conditions, pitcher data, SABR metrics
- ✅ Uses .NET ML framework for machine learning model
- ✅ Output: Enhanced Elo+ ratings

##### 5. 🔲 Data Storage & Persistence
- 🔲 **Model Storage**: Serialize ML.NET model for incremental training
- 🔲 **Rating Storage**: Store both Elo and Elo+ ratings
- ✅ **Precision**: Ratings stored to hundredths (0.01) decimal places
- 🔲 **Historical Data**: Maintain rating progression over time

##### 6. 🔄 Technology Stack
- ✅ **.NET ML**: Microsoft's machine learning framework (package added)
- ✅ **F# Records**: Immutable data structures for game entities
- ✅ **MongoDB**: Existing database for persistence (package available)
- ✅ **CSV Processing**: Robust parsing with error handling and validation
- 🔲 **Incremental Learning**: Support for adding new data to existing model

### Implementation Plan

1. ✅ **Phase 1**: Clean existing CLI and add basic structure
2. ✅ **Phase 2**: Create game data entities and sample data
3. ✅ **Phase 3**: Implement first-pass traditional Elo calculation
4. ✅ **Phase 4**: Add file input support for CSV data
   - ✅ Add `--file` option to CLI command
   - ✅ Support for reading CSV game data instead of sample data
   - ✅ Maintain backward compatibility with existing sample data
5. ✅ **Phase 5**: Create CSV sample data file
   - ✅ Generate realistic CSV file with game data
   - ✅ Include all required fields: teams, scores, weather, pitcher stats, SABR metrics
   - ✅ Stream-friendly format for large datasets
8. 🔲 **Phase 6**: (may need its own readme to analyze the data, pull all the sabr metrics from different CSVs, etc.) Retrosheet data conversion CLI command
   - Add new CLI command: `convert-retrosheet`
   - Parse raw Retrosheet event files (.EVE, .ROS formats)
   - Extract game results, pitcher stats, and basic metrics
   - Convert to standardized CSV format matching test.csv structure
   - Support batch processing of multiple seasons
9. 🔲 **Phase 7**: Add streaming and progress reporting
   - Stream CSV file processing (don't load entire file into memory)
   - Progress reporting every 10th line (configurable via CLI option)
   - Error handling and reporting for malformed data
   - Performance metrics: total games processed and processing time
10. ✅ **Phase 8**: Build ML.NET model for second-pass enhancement
   - ✅ Create ML.NET training data structure with features:
     - ✅ Traditional Elo ratings (home/away team)
     - ✅ Weather factors (temperature, wind speed/direction, precipitation)
     - ✅ Pitcher performance metrics (ERA, WHIP, K/BB ratio, innings pitched)
     - ✅ Team offensive metrics (OPS differential)
     - ✅ Team pitching metrics (ERA+, FIP differential)
   - ✅ Implement binary classification model for game outcome prediction
   - ✅ Feature engineering: normalize values, create interaction terms
   - ✅ Model selection: Logistic Regression implementation
   - ✅ Cross-validation with temporal splitting (chronological order maintained)
   - ✅ Generate Elo+ adjustment factors based on ML model confidence
   - ✅ Integration with existing Elo calculation pipeline
11. ✅ **Phase 9**: Implement model training and validation
   - ✅ Split historical data into 80% training and 20% validation sets
   - ✅ Train ML.NET model on 80% of chronologically ordered games
   - ✅ Validate model accuracy against remaining 20% of games
   - ✅ Report prediction accuracy, precision, recall, and F1 scores
   - ✅ Compare Elo+ predictions vs traditional Elo performance
12. 🔲 **Phase 10**: Add model persistence and rating storage
13. 🔲 **Phase 11**: Testing and validation with larger datasets
7. 🔲 **Phase 12**: JSON-based game prediction CLI command
   - Add new CLI command: `predict-game`
   - Accept JSON game data (file or inline)
   - Return winner prediction with certainty percentage
   - Include detailed prediction breakdown and factors
6. 🔲 **Phase 13**: Move core logic to shared project
   - Extract Elo calculation logic to Fastbreak.Shared project
   - Create shared interfaces for rating calculations
   - Enable API integration for web services
   - Maintain CLI compatibility with shared components

### ML.NET Model Architecture (Phase 8)

#### Feature Engineering Strategy
```fsharp
type GameFeatures = {
    // Elo-based features
    HomeElo: float32
    AwayElo: float32
    EloDifference: float32
    
    // Weather features (normalized)
    Temperature: float32         // Normalized to 0-1 range
    WindSpeed: float32          // Normalized to 0-1 range
    WindFactor: float32         // Directional wind impact (-1 to 1)
    PrecipitationLevel: float32 // Categorical: 0=none, 0.5=light, 1=heavy
    
    // Pitcher features
    HomeERAAdvantage: float32   // (League avg ERA - Home ERA) / League avg
    AwayERAAdvantage: float32   // (League avg ERA - Away ERA) / League avg
    HomeWHIPAdvantage: float32  // Similar normalization
    AwayWHIPAdvantage: float32
    HomeStrikeoutRate: float32  // K/9 normalized
    AwayStrikeoutRate: float32
    
    // Team offensive features
    OPSDifferential: float32    // HomeOPS - AwayOPS
    WOBADifferential: float32   // HomeWOBA - AwayWOBA
    ERAPlus Differential: float32 // HomeERA+ - AwayERA+
    FIPDifferential: float32    // AwayFIP - HomeFIP (lower is better)
    
    // Interaction terms
    EloWeatherInteraction: float32      // Elo difference * weather factor
    PitcherMatchupAdvantage: float32    // Combined pitcher advantage score
}

type GamePrediction = {
    HomeWinProbability: float32
    Label: bool  // true = home win, false = away win
}
```

#### Model Training Pipeline
1. **Data Preprocessing**: Convert GameData to GameFeatures with normalization
2. **Feature Selection**: Use ML.NET feature importance analysis
3. **Model Training**: Train multiple algorithms in parallel:
   - FastTree (gradient boosting)
   - LightGBM (if available)
   - Logistic Regression (baseline)
4. **Model Evaluation**: Cross-validation with temporal splits
5. **Hyperparameter Tuning**: Grid search for optimal parameters
6. **Elo+ Integration**: Use model confidence to adjust traditional Elo ratings

#### Performance Targets
- **Accuracy**: >62% on validation set (baseline: ~54% for random)
- **AUC-ROC**: >0.65 (Area Under Curve for binary classification)
- **Precision/Recall**: Balanced performance for both home/away wins
- **Training Speed**: <30 seconds for 10,000 games on standard hardware

### Sample Data Structure

```fsharp
type GameData = {
    GameId: string
    HomeTeam: string
    AwayTeam: string
    HomeScore: int
    AwayScore: int
    Date: DateTime
    Weather: WeatherData option
    HomePitcher: PitcherData option
    AwayPitcher: PitcherData option
    Metrics: SabrMetrics option
}

type WeatherData = {
    Temperature: float
    WindSpeed: float
    WindDirection: string
    Precipitation: float
}

type EloRating = {
    Team: string
    StandardElo: decimal
    EloPlus: decimal option
    LastUpdated: DateTime
}
```

### Success Metrics
- ✅ Accurate Elo calculations matching traditional implementations
- ✅ Performance suitable for batch processing of historical data
- ✅ Ratings stored with proper precision (thousandths)
- ✅ CSV file parsing with comprehensive error handling
- ✅ Error handling for malformed data (red text, graceful failure)
- ✅ Real MLB data processing (25+ games with full stats)
- 🔲 CSV file streaming and progress reporting
- ✅ ML model successfully incorporates additional features
- ✅ Model achieves >60% prediction accuracy on validation set (achieved 60% vs 52% baseline)
- ✅ Elo+ outperforms traditional Elo by 8% accuracy improvement
- ✅ Model can be retrained with new data (auto-retrains per session)
- ✅ ML confidence-based Elo adjustments working (±11 point range)
- ✅ Feature engineering with 18 normalized features implemented

### Expected CSV Format

```csv
GameId,Date,HomeTeam,AwayTeam,HomeScore,AwayScore,Temperature,WindSpeed,WindDirection,Precipitation,HomePitcherName,HomePitcherERA,HomePitcherWHIP,HomePitcherK,HomePitcherBB,HomePitcherIP,AwayPitcherName,AwayPitcherERA,AwayPitcherWHIP,AwayPitcherK,AwayPitcherBB,AwayPitcherIP,HomeOPS,AwayOPS,HomeWOBA,AwayWOBA,HomeERAPlus,AwayERAPlus,HomeFIP,AwayFIP
2024-04-15-NYY-BOS,2024-04-15,Boston Red Sox,New York Yankees,7,4,72.0,8.5,SW,0.0,Ace Starter,2.85,1.12,185,45,210.1,Control Lefty,3.42,1.25,142,38,178.2,0.785,0.732,0.345,0.328,112,98,3.45,4.12
```

### Performance Expectations
- **Streaming Processing**: Handle files of any size without memory constraints
- **Progress Reporting**: Real-time feedback for long-running operations
- **Error Resilience**: Continue processing after encountering malformed lines
- **Performance Metrics**: Track processing speed and completion statistics

This system will provide enhanced baseball team ratings that go beyond simple win/loss records to include the rich contextual data that affects game outcomes.

## Retrosheet to Sabermetrics

### Overview
Converting Retrosheet's play-by-play data into point-in-time sabermetrics for comprehensive game context analysis. Each calculation must reflect the statistical state at the moment before each game, creating a temporal snapshot of team and player performance.

### Phase 1: Win Streak Analysis
**Objective**: Calculate team win/loss streaks at game time
- Parse chronological game results from event files (.EVE)
- Track consecutive wins/losses leading up to each game
- Handle series breaks and season boundaries
- Output: `CurrentWinStreak` (positive) or `CurrentLossStreak` (negative)

### Phase 2: Starting Pitcher Metrics (Point-in-Time)
**Objective**: Calculate pitcher ERA, WHIP, FIP, and WAR before each start
- **ERA**: Earned runs × 9 ÷ innings pitched (season-to-date)
- **WHIP**: (Walks + hits) ÷ innings pitched
- **FIP**: ((13×HR) + (3×BB) - (2×K)) ÷ IP + FIP constant
- **WAR**: Wins Above Replacement using innings pitched and run prevention
- Parse individual pitcher event data from play-by-play records
- Calculate cumulative stats from season start to current game date

### Phase 3: Impact Player Offensive Metrics
**Objective**: Calculate key position player stats at game time
- **OPS**: On-base percentage + slugging percentage
- **wOBA**: Weighted on-base average using linear weights
- **WAR**: Offensive + defensive + baserunning + positional value
- **ISO**: Isolated power (slugging - batting average)
- Identify "impact players" (top 3-4 position players by plate appearances)
- Calculate running totals from season start to current game

### Phase 4: Advanced Team Metrics
**Objective**: Generate team-level sabermetrics for game context
- **Team wRC+**: Weighted runs created adjusted for park/league
- **Defensive Efficiency**: Outs recorded ÷ balls in play
- **Base Running Value**: Extra bases taken vs. expected
- **Clutch Performance**: Performance in high-leverage situations
- **Recent Form**: Weighted performance over last 10-15 games

### Phase 5: Environmental Context Integration
**Objective**: Incorporate park factors and weather impact
- Parse weather data from event files (temperature, wind, precipitation)
- Apply park factors from reference files
- Calculate home field advantage metrics
- Day vs. night game performance differentials

### Implementation Considerations
- **Temporal Accuracy**: All stats calculated using only games prior to current date
- **Data Validation**: Handle missing data and statistical edge cases
- **Performance**: Optimize for processing 100+ years of baseball data
- **Accuracy**: Cross-validate against published statistics where available

## Retrosheet Integration with Elo+ Ratings

### How Enhanced Features Improve Elo Adjustments

The ML model doesn't directly change Elo ratings - instead, it uses confidence scores to create **Elo adjustments** that modify how much teams gain/lose after each game. The Retrosheet sabermetrics data dramatically enhances this process:

#### Current Elo+ Process
1. **Traditional Elo**: Teams gain/lose points based on wins/losses (typically ±16-32 points)
2. **ML Confidence Score**: Model predicts game outcome with confidence (0.0-1.0)  
3. **Elo Adjustment**: Confidence score creates modifier (currently ±11 point range)

#### Enhanced Process with Retrosheet Data

**Example Game: Boston Red Sox vs New York Yankees**

**Traditional Elo Only:**
- Red Sox win: +24 points
- Yankees lose: -24 points

**With Enhanced ML Features:**
```
ML Model Input:
- Elo difference: Yankees +45
- Win streak: Red Sox on 6-game streak
- Starting pitchers: Sale (2.85 ERA, 3.2 WAR) vs Cole (3.20 ERA, 2.8 WAR)
- Key players: Devers (145 wRC+) vs Judge (180 wRC+)  
- Weather: 15mph wind blowing out
- Park factor: Fenway favors lefties (Sale)
- Recent form: Red Sox 8-2 last 10, Yankees 4-6 last 10

ML Confidence: 72% Red Sox win (higher than Elo alone predicted)
```

**Elo Adjustment Result:**
- High confidence = larger rating adjustments
- Red Sox win gets **+28 points** (instead of +24)
- Yankees lose **-28 points** (instead of -24)
- Model "rewards" the upset more because it correctly identified Red Sox advantages

### Key Enhancement Benefits

1. **Momentum Recognition**: Win streaks and recent form help identify when teams are playing above/below their rating
2. **Injury Impact**: Star player absence (low WAR contributions) significantly affects expected performance  
3. **Matchup Advantages**: Specific pitcher vs. lineup strengths (lefty vs. righty, power vs. finesse)
4. **Environmental Factors**: Weather and park effects that traditional Elo completely misses
5. **Situational Context**: Clutch performance and high-leverage situation data

### Expected Performance Improvements

- **Current Accuracy**: 60% (vs 52% baseline)
- **With Retrosheet Features**: Projected 65-68% accuracy
- **Better Calibration**: More accurate confidence scores lead to more precise Elo adjustments
- **Reduced Variance**: Point-in-time stats eliminate end-of-season statistical noise

The richer the ML features, the better the model identifies when upsets are "actually not upsets" - leading to more accurate rating adjustments over time and faster convergence to true team strength.

## Data Generation Pipeline

### Overview
While Retrosheet provides comprehensive play-by-play data, it doesn't include pre-calculated sabermetrics. We need a data generation pipeline that produces CSV files matching the `test.csv` structure with all necessary features for the Elo+ system.

### Required Output Format (30 columns)
```csv
GameId,Date,HomeTeam,AwayTeam,HomeScore,AwayScore,Temperature,WindSpeed,WindDirection,Precipitation,HomePitcherName,HomePitcherERA,HomePitcherWHIP,HomePitcherK,HomePitcherBB,HomePitcherIP,AwayPitcherName,AwayPitcherERA,AwayPitcherWHIP,AwayPitcherK,AwayPitcherBB,AwayPitcherIP,HomeOPS,AwayOPS,HomeWOBA,AwayWOBA,HomeERAPlus,AwayERAPlus,HomeFIP,AwayFIP
```

### Data Source Strategy

#### Option 1: Multiple Data Source Integration
**Primary Sources:**
- **Baseball Reference** - Historical team/player stats, park factors
- **FanGraphs** - Advanced sabermetrics (FIP, WAR, wRC+, etc.)
- **Weather APIs** - Historical weather data for game locations
- **MLB Stats API** - Recent game results and basic stats

**Challenges:**
- API rate limits and data availability
- Inconsistent data formats across sources
- Historical data gaps (pre-2000s sabermetrics)
- Point-in-time calculation complexity

#### Option 2: Retrosheet + Sabermetrics Calculation Engine
**Approach:**
- Use Retrosheet play-by-play as foundation
- Build calculation engine for sabermetrics from raw events
- Generate point-in-time stats for each game date
- Integrate weather data from historical APIs

**Phases:**
1. **Parse Retrosheet Events** - Extract game results, player actions
2. **Calculate Basic Stats** - Batting averages, ERAs, innings pitched
3. **Derive Advanced Metrics** - FIP, WAR, wOBA, wRC+ from formulas
4. **Point-in-Time Assembly** - Generate stats as they existed at game time
5. **Weather Integration** - Match historical weather to game locations/dates

#### Option 3: BaseballR Package Integration
**Approach:**
- **R baseballr Package**: Point-in-time sabermetrics from Baseball-Reference
- **Data Coverage**: Historical data back to 1994 (some functions to 1992)
- **Functions**: `bref_daily_pitcher()` and `bref_daily_batter()` for date ranges
- **Output**: 46+ columns of comprehensive stats including advanced metrics

**Key Functions:**
```r
bref_daily_pitcher("2024-04-01", "2024-04-01")  # Single game date
bref_daily_batter("2024-04-01", "2024-04-01")   # Point-in-time batting stats
```

**Advantages:**
- Pre-calculated sabermetrics (wOBA, FIP, WHIP, advanced metrics)
- Point-in-time accuracy (stats as of specific date)
- No complex calculation engine required
- Comprehensive 46-column dataset per function call

**Implementation Strategy:**
- Start with 1 season (162 games × 2 API calls = 324 requests)
- Conservative rate limiting (1 request per 5 seconds = ~27 minutes per season)
- Validate data quality against known benchmarks
- Expand to multiple seasons after proof of concept

#### Option 4: Hybrid Approach (Alternative)
**Strategy:**
- **Modern Era (2008-present)**: Use MLB APIs + FanGraphs for rich data
- **Historical Era (1994-2007)**: BaseballR package for point-in-time stats
- **Pre-1994 Era**: Calculate from Retrosheet + basic formulas
- **Weather Data**: Historical APIs for all eras
- **Validation**: Cross-check calculated stats against published sources

### Implementation Phases

#### Phase 1: BaseballR Integration Proof of Concept (2024 Season)
- **Target**: Generate 2,430 games (162 × 15 teams × 2 functions) with full sabermetrics
- **Sources**: BaseballR package (`bref_daily_pitcher`, `bref_daily_batter`)
- **Rate Limiting**: 1 request per 5 seconds (conservative)
- **Timeline**: 1-2 weeks development + 27 minutes per season execution
- **Output**: CSV matching test.csv format with point-in-time accuracy

#### Phase 1b: Modern Era Data Pipeline (2008-2024)
- **Target**: Generate 40,000+ games with full sabermetrics
- **Sources**: MLB Stats API, Baseball Reference scraping
- **Output**: CSV matching test.csv format
- **Timeline**: 2-3 weeks development

#### Phase 2: Historical Calculation Engine
- **Target**: Retrosheet events → sabermetrics calculations
- **Scope**: Essential metrics (ERA, FIP, OPS, basic WAR approximations)
- **Challenge**: Point-in-time accuracy across 100+ years
- **Timeline**: 4-6 weeks development

#### Phase 3: Weather Data Integration
- **Sources**: Weather Underground, NOAA historical data
- **Matching**: Game location + date → temperature, wind, precipitation
- **Fallback**: Seasonal averages for missing data
- **Timeline**: 1-2 weeks development

#### Phase 4: Data Quality & Validation
- **Validation**: Compare calculated vs. published statistics
- **Error Handling**: Missing data interpolation strategies  
- **Performance**: Optimize for processing 150,000+ historical games
- **Timeline**: 2-3 weeks testing/refinement

### Expected Challenges

1. **Point-in-Time Accuracy**: Ensuring stats reflect game-day knowledge, not season-end
2. **Data Completeness**: Historical gaps in advanced metrics
3. **Calculation Complexity**: WAR requires multiple components and league adjustments
4. **Performance**: Processing decades of play-by-play data efficiently
5. **Weather Matching**: Accurate historical weather for specific game times/locations

### Success Metrics
- **Coverage**: 95%+ games have complete feature data
- **Accuracy**: Calculated stats within 5% of published values (where available)
- **Performance**: Process full historical dataset in <2 hours
- **Format Consistency**: All output matches test.csv structure exactly
- **ML Readiness**: Generated data immediately usable by Elo+ pipeline

This data generation pipeline is the critical bridge between raw baseball data and the ML-powered Elo+ rating system.