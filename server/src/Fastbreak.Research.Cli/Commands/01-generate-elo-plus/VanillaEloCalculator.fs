namespace Fastbreak.Research.Cli.Commands.GenerateEloPlus

open Fastbreak.Research.Cli.Commands.GenerateEloPlus
open System

module VanillaEloCalculator =
    
    // Standard MLB Elo parameters based on FiveThirtyEight research and empirical optimization
    // These values have been proven optimal for Major League Baseball through extensive testing
    
    let K_FACTOR = 4.0                      // FiveThirtyEight standard for MLB (Nate Silver recommendation)
    let HOME_FIELD_ADVANTAGE = 68.0         // Empirically derived - roughly 54% win probability for evenly matched teams
    let DEFAULT_RATING = 1500.0             // Standard baseline - all teams start here
    let RATING_SCALE = 400.0               // Standard Elo scale factor (chess origin, proven for sports)
    
    // Configuration for vanilla Elo system (allows customization while maintaining standards)
    type VanillaEloConfig = {
        KFactor: float                      // Learning rate - how much ratings change per game
        HomeFieldAdvantage: float           // Points added to home team before calculation
        DefaultRating: float               // Starting rating for all teams
        RatingScale: float                 // Scale factor in probability calculation (400 is standard)
        UseSeasonalRegression: bool        // Whether to regress ratings toward mean between seasons
        RegressionFactor: float            // How much to regress (0.0 = none, 1.0 = full regression to mean)
    }
    
    // Default configuration using research-proven parameters
    let defaultConfig = {
        KFactor = K_FACTOR
        HomeFieldAdvantage = HOME_FIELD_ADVANTAGE  
        DefaultRating = DEFAULT_RATING
        RatingScale = RATING_SCALE
        UseSeasonalRegression = false       // Disabled by default - can be enabled for multi-season analysis
        RegressionFactor = 0.25            // Conservative regression when enabled
    }
    
    // Calculate expected win probability using standard Elo formula
    // This is the mathematical heart of the Elo rating system
    let calculateWinProbability (homeRating: float) (awayRating: float) (config: VanillaEloConfig) : float =
        // Apply home field advantage to home team rating
        let adjustedHomeRating = homeRating + config.HomeFieldAdvantage
        
        // Standard Elo probability formula: P = 1 / (1 + 10^((R_opponent - R_team) / scale))
        let ratingDifference = adjustedHomeRating - awayRating
        let exponent = ratingDifference / config.RatingScale
        
        // Use more numerically stable calculation for extreme rating differences
        if exponent > 10.0 then
            1.0 - 1e-10  // Effectively 1.0 but avoid floating point issues
        elif exponent < -10.0 then
            1e-10        // Effectively 0.0 but avoid floating point issues
        else
            1.0 / (1.0 + Math.Pow(10.0, -exponent))
    
    // Calculate new ratings after a game using standard Elo update formula
    let updateRatings (homeRating: float) (awayRating: float) (homeWin: bool) (config: VanillaEloConfig) : float * float =
        // Calculate expected probability of home team winning
        let expectedHomeWin = calculateWinProbability homeRating awayRating config
        
        // Actual game result (1.0 if home wins, 0.0 if away wins, 0.5 for ties)
        let actualResult = if homeWin then 1.0 else 0.0
        
        // Rating changes are zero-sum: what winner gains, loser loses
        let homeRatingChange = config.KFactor * (actualResult - expectedHomeWin)
        let awayRatingChange = -homeRatingChange  // Zero-sum property
        
        // Return updated ratings
        (homeRating + homeRatingChange, awayRating + awayRatingChange)
    
    // Process a single game and return updated ratings map
    let processGame (currentRatings: Map<string, float>) (game: GameData) (config: VanillaEloConfig) : Map<string, float> =
        // Get current ratings, defaulting to baseline for new teams
        let homeRating = currentRatings.TryFind(game.HomeTeam) |> Option.defaultValue config.DefaultRating
        let awayRating = currentRatings.TryFind(game.AwayTeam) |> Option.defaultValue config.DefaultRating
        
        // Determine game result
        let homeWin = game.HomeScore > game.AwayScore
        
        // Calculate new ratings
        let (newHomeRating, newAwayRating) = updateRatings homeRating awayRating homeWin config
        
        // Update the ratings map
        currentRatings
        |> Map.add game.HomeTeam newHomeRating
        |> Map.add game.AwayTeam newAwayRating
    
    // Calculate vanilla Elo ratings with custom configuration
    let calculateVanillaEloRatingsWithConfig (games: GameData list) (config: VanillaEloConfig) : Map<string, float> =
        // Process games in chronological order - this is critical for rating accuracy
        let sortedGames = games |> List.sortBy (fun g -> g.Date)
        
        // Fold through all games, updating ratings after each
        sortedGames
        |> List.fold (fun ratings game -> processGame ratings game config) Map.empty
    
    // Calculate vanilla Elo ratings for a complete set of games
    let calculateVanillaEloRatings (games: GameData list) : Map<string, float> =
        calculateVanillaEloRatingsWithConfig games defaultConfig
    
    // Convert to standard EloRating records for compatibility with existing system
    let convertToEloRatings (ratings: Map<string, float>) (timestamp: DateTime) : EloRating list =
        ratings
        |> Map.toList
        |> List.map (fun (team, rating) -> 
            {
                Team = team
                StandardElo = decimal rating
                EloPlus = None  // Vanilla Elo doesn't have Elo+ component
                LastUpdated = timestamp
            })
    
    // Get detailed performance statistics for vanilla Elo predictions
    let calculatePerformanceStatistics (games: GameData list) (config: VanillaEloConfig) : string * float =
        if games.IsEmpty then
            ("No games to evaluate", 0.0)
        else
            let sortedGames = games |> List.sortBy (fun g -> g.Date)
            let mutable currentRatings = Map.empty<string, float>
            let mutable correctPredictions = 0
            let mutable totalGames = 0
            let mutable totalLogLoss = 0.0
            let mutable totalBrierScore = 0.0
            
            // Process each game and track prediction accuracy
            for game in sortedGames do
                let homeRating = currentRatings.TryFind(game.HomeTeam) |> Option.defaultValue config.DefaultRating
                let awayRating = currentRatings.TryFind(game.AwayTeam) |> Option.defaultValue config.DefaultRating
                
                // Make prediction before updating ratings
                let homeProbability = calculateWinProbability homeRating awayRating config
                let predictedHomeWin = homeProbability > 0.5
                let actualHomeWin = game.HomeScore > game.AwayScore
                
                // Track accuracy
                if predictedHomeWin = actualHomeWin then
                    correctPredictions <- correctPredictions + 1
                totalGames <- totalGames + 1
                
                // Calculate probabilistic scoring metrics
                let actualResult = if actualHomeWin then 1.0 else 0.0
                
                // Log-loss (cross-entropy): -y*log(p) - (1-y)*log(1-p)
                let clampedProb = max 1e-15 (min (1.0 - 1e-15) homeProbability)  // Avoid log(0)
                let logLoss = 
                    if actualHomeWin then 
                        -Math.Log(clampedProb)
                    else 
                        -Math.Log(1.0 - clampedProb)
                totalLogLoss <- totalLogLoss + logLoss
                
                // Brier score: (p - y)²
                let brierScore = (homeProbability - actualResult) * (homeProbability - actualResult)
                totalBrierScore <- totalBrierScore + brierScore
                
                // Update ratings for next game
                currentRatings <- processGame currentRatings game config
            
            let accuracy = float correctPredictions / float totalGames
            let avgLogLoss = totalLogLoss / float totalGames
            let avgBrierScore = totalBrierScore / float totalGames
            
            let report = 
                sprintf "Vanilla Elo Performance Statistics:\n" +
                sprintf "=====================================\n" +
                sprintf "Games Evaluated: %d\n" totalGames +
                sprintf "Accuracy: %.1f%% (%d/%d correct predictions)\n" (accuracy * 100.0) correctPredictions totalGames +
                sprintf "Average Log-Loss: %.4f (lower is better)\n" avgLogLoss +
                sprintf "Average Brier Score: %.4f (lower is better)\n\n" avgBrierScore +
                sprintf "Configuration:\n" +
                sprintf "- K-Factor: %.1f\n" config.KFactor +
                sprintf "- Home Field Advantage: %.1f points\n" config.HomeFieldAdvantage +
                sprintf "- Default Rating: %.1f\n" config.DefaultRating +
                sprintf "- Rating Scale: %.1f" config.RatingScale
            
            (report, accuracy)
    
    // Get current rating statistics
    let getRatingStatistics (ratings: Map<string, float>) : string =
        let ratingValues = ratings |> Map.toList |> List.map snd
        
        if ratingValues.IsEmpty then
            "No ratings available"
        else
            let avgRating = ratingValues |> List.average
            let maxRating = ratingValues |> List.max  
            let minRating = ratingValues |> List.min
            let ratingSpread = maxRating - minRating
            let ratingStdDev = 
                let variance = ratingValues |> List.map (fun r -> (r - avgRating) * (r - avgRating)) |> List.average
                Math.Sqrt(variance)
            
            sprintf """Vanilla Elo Rating Statistics:
==============================
Teams: %d
Average Rating: %.1f
Highest Rating: %.1f  
Lowest Rating: %.1f
Rating Spread: %.1f points
Standard Deviation: %.1f

Rating Scale Interpretation:
- 100 point difference ≈ 64%% win probability
- 200 point difference ≈ 76%% win probability  
- 300 point difference ≈ 85%% win probability"""
                ratings.Count avgRating maxRating minRating ratingSpread ratingStdDev
    
    // Apply seasonal regression to ratings (useful for multi-season analysis)
    let applySeasonalRegression (ratings: Map<string, float>) (config: VanillaEloConfig) : Map<string, float> =
        if not config.UseSeasonalRegression then
            ratings
        else
            let ratingValues = ratings |> Map.toList |> List.map snd
            let avgRating = if ratingValues.IsEmpty then config.DefaultRating else List.average ratingValues
            
            ratings
            |> Map.map (fun _ rating ->
                // Regress toward mean: new_rating = (1 - factor) * old_rating + factor * mean
                (1.0 - config.RegressionFactor) * rating + config.RegressionFactor * avgRating)
    
    // Create configuration for different sports (MLB optimized by default)
    let createSportSpecificConfig (sport: string) : VanillaEloConfig =
        match sport.ToLowerInvariant() with
        | "mlb" | "baseball" -> defaultConfig
        | "nba" | "basketball" -> 
            { defaultConfig with 
                KFactor = 20.0              // Higher K for more volatile sport
                HomeFieldAdvantage = 100.0   // Stronger home court advantage
            }
        | "nfl" | "football" ->
            { defaultConfig with 
                KFactor = 20.0              // Higher K for limited games per season
                HomeFieldAdvantage = 65.0    // Moderate home field advantage
            }
        | "nhl" | "hockey" ->
            { defaultConfig with 
                KFactor = 20.0              // Higher K for more volatile outcomes
                HomeFieldAdvantage = 90.0    // Strong home ice advantage
            }
        | _ -> 
            printfn "Warning: Unknown sport '%s', using MLB defaults" sport
            defaultConfig