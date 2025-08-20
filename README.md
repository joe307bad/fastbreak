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

##### 4. 🔄 Two-Pass Elo Calculation

**✅ First Pass: Traditional Elo**
- ✅ Input: Game results (wins/losses only)
- ✅ Output: Standard Elo ratings
- ✅ Uses classic Elo algorithm with K-factor adjustment
- ✅ Stores initial ratings for reference

**🔲 Second Pass: Elo+ Enhancement**
- 🔲 Input: First pass Elo ratings + enhanced features
- 🔲 Features: Weather conditions, pitcher data, SABR metrics
- 🔲 Uses .NET ML framework for machine learning model
- 🔲 Output: Enhanced Elo+ ratings

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
10. 🔲 **Phase 8**: Build ML.NET model for second-pass enhancement
   - Create ML.NET training data structure with features:
     - Traditional Elo ratings (home/away team)
     - Weather factors (temperature, wind speed/direction, precipitation)
     - Pitcher performance metrics (ERA, WHIP, K/BB ratio, innings pitched)
     - Team offensive metrics (OPS differential)
     - Team pitching metrics (ERA+, FIP differential)
   - Implement binary classification model for game outcome prediction
   - Feature engineering: normalize values, create interaction terms
   - Model selection: compare Logistic Regression, Decision Tree, Fast Tree
   - Cross-validation with temporal splitting (chronological order maintained)
   - Generate Elo+ adjustment factors based on ML model confidence
   - Integration with existing Elo calculation pipeline
11. 🔲 **Phase 9**: Implement model training and validation
   - Split historical data into 80% training and 20% validation sets
   - Train ML.NET model on 80% of chronologically ordered games
   - Validate model accuracy against remaining 20% of games
   - Report prediction accuracy, precision, recall, and F1 scores
   - Compare Elo+ predictions vs traditional Elo performance
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
- 🔲 ML model successfully incorporates additional features
- 🔲 Model achieves >65% prediction accuracy on validation set
- 🔲 Elo+ outperforms traditional Elo by >5% accuracy
- 🔲 Model can be retrained with new data

### Expected CSV Format

```csv
GameId,Date,HomeTeam,AwayTeam,HomeScore,AwayScore,Temperature,WindSpeed,WindDirection,Precipitation,HomePitcherName,HomePitcherERA,HomePitcherWHIP,HomePitcherK,HomePitcherBB,HomePitcherIP,AwayPitcherName,AwayPitcherERA,AwayPitcherWHIP,AwayPitcherK,AwayPitcherBB,AwayPitcherIP,HomeOPS,AwayOPS,HomeERAPlus,AwayERAPlus,HomeFIP,AwayFIP
2024-04-15-NYY-BOS,2024-04-15,Boston Red Sox,New York Yankees,7,4,72.0,8.5,SW,0.0,Ace Starter,2.85,1.12,185,45,210.1,Control Lefty,3.42,1.25,142,38,178.2,0.785,0.732,112,98,3.45,4.12
```

### Performance Expectations
- **Streaming Processing**: Handle files of any size without memory constraints
- **Progress Reporting**: Real-time feedback for long-running operations
- **Error Resilience**: Continue processing after encountering malformed lines
- **Performance Metrics**: Track processing speed and completion statistics

This system will provide enhanced baseball team ratings that go beyond simple win/loss records to include the rich contextual data that affects game outcomes.