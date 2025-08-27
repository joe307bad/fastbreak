namespace Fastbreak.Cli.Services

open Fastbreak.Cli.Entities
open Fastbreak.Cli.Services
open System

module FeatureEngineering =
    
    // Helper functions for normalization
    
    let calculateERAAdvantage (era: float) : float32 =
        (FeatureConstants.LEAGUE_AVG_ERA - float32 era) / FeatureConstants.LEAGUE_AVG_ERA
    
    let calculateWHIPAdvantage (whip: float) : float32 =
        (FeatureConstants.LEAGUE_AVG_WHIP - float32 whip) / FeatureConstants.LEAGUE_AVG_WHIP
    
    let calculateStrikeoutRate (strikeouts: int) (inningsPitched: float) : float32 =
        if inningsPitched > 0.0 then
            let kPer9 = (float strikeouts * 9.0) / inningsPitched
            float32 (kPer9 / float FeatureConstants.LEAGUE_AVG_K_PER_9)
        else
            1.0f  // Neutral if no data
    
    // Convert GameData to GameFeatures for ML.NET
    let convertToFeatures (eloRatings: Map<string, decimal>) (game: GameData) : GameFeatures =
        // Get Elo ratings
        let homeElo = eloRatings.TryFind(game.HomeTeam) |> Option.defaultValue EloCalculator.DEFAULT_RATING
        let awayElo = eloRatings.TryFind(game.AwayTeam) |> Option.defaultValue EloCalculator.DEFAULT_RATING
        let eloDiff = homeElo - awayElo
        
        
        // Pitcher features
        let (homeERAAdv, awayERAAdv, homeWHIPAdv, awayWHIPAdv, homeKRate, awayKRate) =
            match game.HomePitcher, game.AwayPitcher with
            | Some hp, Some ap ->
                let homeERA = calculateERAAdvantage hp.ERA
                let awayERA = calculateERAAdvantage ap.ERA
                let homeWHIP = calculateWHIPAdvantage hp.WHIP
                let awayWHIP = calculateWHIPAdvantage ap.WHIP
                let homeK = calculateStrikeoutRate hp.Strikeouts hp.InningsPitched
                let awayK = calculateStrikeoutRate ap.Strikeouts ap.InningsPitched
                (homeERA, awayERA, homeWHIP, awayWHIP, homeK, awayK)
            | _ -> (0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f)  // Neutral values if no data
        
        // Team metrics features
        let (opsDiff, eraPlusDiff, fipDiff) =
            match game.Metrics with
            | Some m ->
                let ops = float32 (m.HomeOPS - m.AwayOPS)
                let eraPlus = float32 (m.HomeERAPlus - m.AwayERAPlus)
                let fip = float32 (m.AwayFIP - m.HomeFIP)  // Lower FIP is better, so away - home
                (ops, eraPlus, fip)
            | None -> (0.0f, 0.0f, 0.0f)  // Neutral values
        
        // Interaction terms
        let pitcherMatchupAdvantage = (homeERAAdv - awayERAAdv) + (homeWHIPAdv - awayWHIPAdv) + (homeKRate - awayKRate)
        
        // Label (target variable)
        let homeWin = game.HomeScore > game.AwayScore
        
        {
            HomeElo = float32 homeElo
            AwayElo = float32 awayElo
            EloDifference = float32 eloDiff
            HomeERAAdvantage = homeERAAdv
            AwayERAAdvantage = awayERAAdv
            HomeWHIPAdvantage = homeWHIPAdv
            AwayWHIPAdvantage = awayWHIPAdv
            HomeStrikeoutRate = homeKRate
            AwayStrikeoutRate = awayKRate
            OPSDifferential = opsDiff
            ERAPlusDifferential = eraPlusDiff
            FIPDifferential = fipDiff
            PitcherMatchupAdvantage = pitcherMatchupAdvantage
            HomeWin = homeWin
        }
    
    // Convert a list of games to features (with progressive Elo calculation)
    let convertGamesToFeatures (games: GameData list) : GameFeatures list =
        let sortedGames = games |> List.sortBy (fun g -> g.Date)
        let mutable currentRatings = Map.empty<string, decimal>
        
        sortedGames
        |> List.map (fun game ->
            let features = convertToFeatures currentRatings game
            // Update ratings after feature extraction (maintaining chronological order)
            currentRatings <- EloCalculator.processGame currentRatings game
            features)
    
    // Extract feature statistics for debugging
    let getFeatureStatistics (features: GameFeatures list) : string =
        if features.IsEmpty then "No features to analyze"
        else
            let count = features.Length
            let homeWins = features |> List.sumBy (fun f -> if f.HomeWin then 1 else 0)
            let avgEloDiff = features |> List.averageBy (fun f -> float f.EloDifference)
            let avgOPSDiff = features |> List.averageBy (fun f -> float f.OPSDifferential)
            
            sprintf "Feature Statistics:\n- Games: %d\n- Home wins: %d (%.1f%%)\n- Avg Elo diff: %.1f\n- Avg OPS diff: %.3f"
                count homeWins (float homeWins / float count * 100.0) avgEloDiff avgOPSDiff