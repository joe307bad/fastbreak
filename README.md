# Fastbreak

A sports analytics platform with advanced Elo+ rating system for baseball analysis.

## Elo+ Rating System Brainstorm

### Overview
The Elo+ system is a two-pass machine learning approach that extends traditional Elo ratings by incorporating advanced baseball analytics and environmental factors.

### Architecture

#### Current CLI Structure
- **F# CLI Application** (`server/src/Fastbreak.Cli/`)
- **Current Commands**: `Export_Leaderboard`, `Export_Stats` 
- **Framework**: Argu for command-line parsing
- **Target**: .NET 9.0

#### Planned Implementation

##### 1. Remove Example Commands
- Remove existing `Export_Leaderboard` and `Export_Stats` commands
- Clean up DataExport.fs module

##### 2. New CLI Command: `generate-elo-plus`
```
fastbreak-cli generate-elo-plus [options]
```

##### 3. Game Data Entity
Simple entity structure to represent:
- Game results (win/loss)
- Team identifiers
- Weather data (temperature, wind, precipitation)
- Pitcher statistics
- Basic SABR metrics (OPS, ERA+, FIP, etc.)

##### 4. Two-Pass Elo Calculation

**First Pass: Traditional Elo**
- Input: Game results (wins/losses only)
- Output: Standard Elo ratings
- Uses classic Elo algorithm with K-factor adjustment
- Stores initial ratings for reference

**Second Pass: Elo+ Enhancement**
- Input: First pass Elo ratings + enhanced features
- Features: Weather conditions, pitcher data, SABR metrics
- Uses .NET ML framework for machine learning model
- Output: Enhanced Elo+ ratings

##### 5. Data Storage & Persistence
- **Model Storage**: Serialize ML.NET model for incremental training
- **Rating Storage**: Store both Elo and Elo+ ratings
- **Precision**: Ratings stored to hundredths (0.01) decimal places
- **Historical Data**: Maintain rating progression over time

##### 6. Technology Stack
- **.NET ML**: Microsoft's machine learning framework
- **F# Records**: Immutable data structures for game entities
- **MongoDB**: Existing database for persistence
- **Incremental Learning**: Support for adding new data to existing model

### Implementation Plan

1. **Phase 1**: Clean existing CLI and add basic structure
2. **Phase 2**: Create game data entities and sample data
3. **Phase 3**: Implement first-pass traditional Elo calculation
4. **Phase 4**: Build ML.NET model for second-pass enhancement
5. **Phase 5**: Add model persistence and rating storage
6. **Phase 6**: Testing and validation with sample data

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
- Accurate Elo calculations matching traditional implementations
- ML model successfully incorporates additional features
- Ratings stored with proper precision
- Model can be retrained with new data
- Performance suitable for batch processing of historical data

This system will provide enhanced baseball team ratings that go beyond simple win/loss records to include the rich contextual data that affects game outcomes.