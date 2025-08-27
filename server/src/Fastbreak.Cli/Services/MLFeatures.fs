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
    
    
    // Pitcher features
    [<LoadColumn(3)>] HomeERAAdvantage: float32   // (League avg ERA - Home ERA) / League avg
    [<LoadColumn(4)>] AwayERAAdvantage: float32   // (League avg ERA - Away ERA) / League avg
    [<LoadColumn(5)>] HomeWHIPAdvantage: float32  // Similar normalization
    [<LoadColumn(6)>] AwayWHIPAdvantage: float32
    [<LoadColumn(7)>] HomeStrikeoutRate: float32  // K/9 normalized
    [<LoadColumn(8)>] AwayStrikeoutRate: float32
    
    // Team offensive features
    [<LoadColumn(9)>] OPSDifferential: float32    // HomeOPS - AwayOPS
    [<LoadColumn(10)>] ERAPlusDifferential: float32 // HomeERA+ - AwayERA+
    [<LoadColumn(11)>] FIPDifferential: float32    // AwayFIP - HomeFIP (lower is better)
    
    // Interaction terms
    [<LoadColumn(12)>] PitcherMatchupAdvantage: float32    // Combined pitcher advantage score
    
    // Label (target variable)
    [<LoadColumn(13)>] [<ColumnName("Label")>] HomeWin: bool  // true = home win, false = away win
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
    // League average constants (2023 MLB season approximations)
    let LEAGUE_AVG_ERA = 4.28f
    let LEAGUE_AVG_WHIP = 1.31f
    let LEAGUE_AVG_K_PER_9 = 8.8f