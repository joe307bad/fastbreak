namespace Fastbreak.Research.Cli.Commands.GenerateEloPlus

open System
open Fastbreak.Research.Cli.Commands.GenerateEloPlus

module EloCalculator =
    
    // Standard Elo rating constants
    let DEFAULT_RATING = 1600.0m
    let K_FACTOR = 24.0m
    
    // Calculate expected score for a team given their rating vs opponent
    let calculateExpectedScore (teamRating: decimal) (opponentRating: decimal) : decimal =
        let exponent = float (opponentRating - teamRating) / 400.0
        1.0m / (1.0m + decimal (Math.Pow(10.0, exponent)))
    
    // Calculate new Elo rating after a game
    let calculateNewRating (currentRating: decimal) (expectedScore: decimal) (actualScore: decimal) : decimal =
        currentRating + K_FACTOR * (actualScore - expectedScore)
    
    // Process a single game and update ratings
    let processGame (ratings: Map<string, decimal>) (game: GameData) : Map<string, decimal> =
        // Get current ratings or use default
        let homeRating = ratings.TryFind(game.HomeTeam) |> Option.defaultValue DEFAULT_RATING
        let awayRating = ratings.TryFind(game.AwayTeam) |> Option.defaultValue DEFAULT_RATING
        
        // Calculate expected scores
        let homeExpected = calculateExpectedScore homeRating awayRating
        let awayExpected = calculateExpectedScore awayRating homeRating
        
        // Determine actual scores (1.0 for win, 0.5 for tie, 0.0 for loss)
        let homeActual, awayActual = 
            if game.HomeScore > game.AwayScore then (1.0m, 0.0m)
            elif game.HomeScore < game.AwayScore then (0.0m, 1.0m)
            else (0.5m, 0.5m)
        
        // Calculate new ratings
        let newHomeRating = calculateNewRating homeRating homeExpected homeActual
        let newAwayRating = calculateNewRating awayRating awayExpected awayActual
        
        // Update ratings map
        ratings
        |> Map.add game.HomeTeam newHomeRating
        |> Map.add game.AwayTeam newAwayRating
    
    // Calculate Elo ratings for all games in chronological order
    let calculateEloRatings (games: GameData list) : Map<string, decimal> =
        games
        |> List.sortBy (fun g -> g.Date)
        |> List.fold processGame Map.empty
    
    // Convert ratings map to EloRating records
    let convertToEloRatings (ratingsMap: Map<string, decimal>) (timestamp: DateTime) : EloRating list =
        ratingsMap
        |> Map.toList
        |> List.map (fun (team, rating) -> {
            Team = team
            StandardElo = Math.Round(rating, 3)
            EloPlus = None
            LastUpdated = timestamp
        })
    
    // Get game details for debugging
    let getGameDetails (ratings: Map<string, decimal>) (game: GameData) : string =
        let homeRating = ratings.TryFind(game.HomeTeam) |> Option.defaultValue DEFAULT_RATING
        let awayRating = ratings.TryFind(game.AwayTeam) |> Option.defaultValue DEFAULT_RATING
        let homeExpected = calculateExpectedScore homeRating awayRating
        let awayExpected = calculateExpectedScore awayRating homeRating
        
        let homeActual, awayActual = 
            if game.HomeScore > game.AwayScore then (1.0m, 0.0m)
            elif game.HomeScore < game.AwayScore then (0.0m, 1.0m)
            else (0.5m, 0.5m)
        
        let newHomeRating = calculateNewRating homeRating homeExpected homeActual
        let newAwayRating = calculateNewRating awayRating awayExpected awayActual
        
        sprintf "Game: %s (%s) vs %s (%s) | Score: %d-%d | Expected: %.3f-%.3f | New Ratings: %.1f->%.1f, %.1f->%.1f"
            game.AwayTeam (if awayActual = 1.0m then "W" elif awayActual = 0.5m then "T" else "L")
            game.HomeTeam (if homeActual = 1.0m then "W" elif homeActual = 0.5m then "T" else "L")
            game.AwayScore game.HomeScore
            (float awayExpected) (float homeExpected)
            (float awayRating) (float newAwayRating)
            (float homeRating) (float newHomeRating)