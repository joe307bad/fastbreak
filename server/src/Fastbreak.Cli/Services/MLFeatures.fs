namespace Fastbreak.Cli.Services

open Microsoft.ML.Data
open System

// ML.NET input features for game prediction
[<CLIMutable>]
type GameFeatures = {
    // Elo-based features
    [<LoadColumn(0)>] HomeElo: float32
    [<LoadColumn(1)>] AwayElo: float32 
    [<LoadColumn(2)>] EloDifference: float32
    
    // Weather features (normalized)
    [<LoadColumn(3)>] Temperature: float32         // Normalized to 0-1 range
    [<LoadColumn(4)>] WindSpeed: float32          // Normalized to 0-1 range
    [<LoadColumn(5)>] WindFactor: float32         // Directional wind impact (-1 to 1)
    [<LoadColumn(6)>] PrecipitationLevel: float32 // Categorical: 0=none, 0.5=light, 1=heavy
    
    // Pitcher features
    [<LoadColumn(7)>] HomeERAAdvantage: float32   // (League avg ERA - Home ERA) / League avg
    [<LoadColumn(8)>] AwayERAAdvantage: float32   // (League avg ERA - Away ERA) / League avg
    [<LoadColumn(9)>] HomeWHIPAdvantage: float32  // Similar normalization
    [<LoadColumn(10)>] AwayWHIPAdvantage: float32
    [<LoadColumn(11)>] HomeStrikeoutRate: float32  // K/9 normalized
    [<LoadColumn(12)>] AwayStrikeoutRate: float32
    
    // Team offensive features
    [<LoadColumn(13)>] OPSDifferential: float32    // HomeOPS - AwayOPS
    [<LoadColumn(14)>] ERAPlusDifferential: float32 // HomeERA+ - AwayERA+
    [<LoadColumn(15)>] FIPDifferential: float32    // AwayFIP - HomeFIP (lower is better)
    
    // Interaction terms
    [<LoadColumn(16)>] EloWeatherInteraction: float32      // Elo difference * weather factor
    [<LoadColumn(17)>] PitcherMatchupAdvantage: float32    // Combined pitcher advantage score
    
    // Label (target variable)
    [<LoadColumn(18)>] [<ColumnName("Label")>] HomeWin: bool  // true = home win, false = away win
}

// ML.NET prediction output
[<CLIMutable>]
type GamePrediction = {
    [<ColumnName("PredictedLabel")>] HomeWin: bool
    [<ColumnName("Probability")>] HomeWinProbability: float32
    [<ColumnName("Score")>] Score: float32
}

// Constants for normalization
module FeatureConstants =
    // Weather normalization ranges (based on typical baseball weather)
    let MIN_TEMPERATURE = 32.0f  // Freezing point
    let MAX_TEMPERATURE = 110.0f // Extreme heat
    let MAX_WIND_SPEED = 50.0f   // Very high wind
    
    // League average constants (2023 MLB season approximations)
    let LEAGUE_AVG_ERA = 4.28f
    let LEAGUE_AVG_WHIP = 1.31f
    let LEAGUE_AVG_K_PER_9 = 8.8f
    
    // Wind direction impact factors (simplified)
    let getWindFactor (direction: string) (speed: float32) : float32 =
        let factor = 
            match direction.ToUpper() with
            | "N" | "NE" | "NW" -> -0.3f  // Headwind reduces offense
            | "S" | "SE" | "SW" -> 0.3f   // Tailwind helps offense  
            | "E" | "W" -> 0.0f           // Cross wind neutral
            | _ -> 0.0f
        factor * (speed / MAX_WIND_SPEED) // Scale by wind speed