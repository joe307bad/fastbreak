# Fastbreak

A sports analytics platform with advanced Elo+ rating system for baseball analysis.

## Elo+ Rating System Brainstorm

### Overview
The Elo+ system is a two-pass machine learning approach that extends traditional Elo ratings by incorporating advanced baseball analytics and environmental factors.

### Architecture

#### Current CLI Structure
- **F# CLI Application** (`server/src/Fastbreak.Cli/`)
- **Current Commands**: ✅ `generate-elo-plus` 
- **Framework**: Argu for command-line parsing
- **Target**: .NET 9.0

#### Implementation Status

##### 1. ✅ Remove Example Commands
- ✅ Remove existing `Export_Leaderboard` and `Export_Stats` commands
- ✅ Clean up DataExport.fs module (renamed to EloPlus.fs)

##### 2. 🔄 New CLI Command: `generate-elo-plus`
```
fastbreak-cli generate-elo-plus [options]

Options:
  --file <path>           CSV file path for game data (optional, uses sample data if not specified)
  --progress <n>          Report progress every N lines (default: 10)
  --help                  Show help information
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
- 🔲 **Incremental Learning**: Support for adding new data to existing model

### Implementation Plan

1. ✅ **Phase 1**: Clean existing CLI and add basic structure
2. ✅ **Phase 2**: Create game data entities and sample data
3. ✅ **Phase 3**: Implement first-pass traditional Elo calculation
4. 🔲 **Phase 4**: Add file input support for CSV data
   - Add `--file` option to CLI command
   - Support for reading CSV game data instead of sample data
   - Maintain backward compatibility with existing sample data
5. 🔲 **Phase 5**: Create CSV sample data file
   - Generate realistic CSV file with game data
   - Include all required fields: teams, scores, weather, pitcher stats, SABR metrics
   - Stream-friendly format for large datasets
6. 🔲 **Phase 6**: Add streaming and progress reporting
   - Stream CSV file processing (don't load entire file into memory)
   - Progress reporting every 10th line (configurable via CLI option)
   - Error handling and reporting for malformed data
   - Performance metrics: total games processed and processing time
7. 🔲 **Phase 7**: Build ML.NET model for second-pass enhancement
8. 🔲 **Phase 8**: Add model persistence and rating storage
9. 🔲 **Phase 9**: Testing and validation with larger datasets

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
- ✅ Ratings stored with proper precision
- 🔲 CSV file streaming and progress reporting
- 🔲 Error handling for malformed data
- 🔲 ML model successfully incorporates additional features
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