namespace Fastbreak.Cli.Services

open Fastbreak.Cli.Entities
open Fastbreak.Cli.Services
open System

module FeatureEngineering =
    
    // Helper functions for normalization
    let normalizeTemperature (temp: float) : float32 =
        let clamped = max (float FeatureConstants.MIN_TEMPERATURE) (min (float FeatureConstants.MAX_TEMPERATURE) temp)
        float32 ((clamped - float FeatureConstants.MIN_TEMPERATURE) / (float FeatureConstants.MAX_TEMPERATURE - float FeatureConstants.MIN_TEMPERATURE))
    
    let normalizeWindSpeed (speed: float) : float32 =
        let clamped = max 0.0 (min (float FeatureConstants.MAX_WIND_SPEED) speed)
        float32 (clamped / float FeatureConstants.MAX_WIND_SPEED)
    
    let normalizePrecipitation (precip: float) : float32 =
        if precip <= 0.0 then 0.0f
        elif precip <= 0.1 then 0.5f  // Light precipitation
        else 1.0f  // Heavy precipitation
    
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
        
        // Weather features
        let weatherFeatures = 
            match game.Weather with
            | Some w -> 
                let temp = normalizeTemperature w.Temperature
                let wind = normalizeWindSpeed w.WindSpeed
                let windFactor = FeatureConstants.getWindFactor w.WindDirection (float32 w.WindSpeed)
                let precip = normalizePrecipitation w.Precipitation
                (temp, wind, windFactor, precip)
            | None -> (0.5f, 0.5f, 0.0f, 0.0f)  // Default neutral values
        
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
        let eloWeatherInteraction = float32 eloDiff * (let _, _, wf, _ = weatherFeatures in wf)
        let pitcherMatchupAdvantage = (homeERAAdv - awayERAAdv) + (homeWHIPAdv - awayWHIPAdv) + (homeKRate - awayKRate)
        
        // Label (target variable)
        let homeWin = game.HomeScore > game.AwayScore
        
        {
            HomeElo = float32 homeElo
            AwayElo = float32 awayElo
            EloDifference = float32 eloDiff
            Temperature = let t, _, _, _ = weatherFeatures in t
            WindSpeed = let _, w, _, _ = weatherFeatures in w
            WindFactor = let _, _, wf, _ = weatherFeatures in wf
            PrecipitationLevel = let _, _, _, p = weatherFeatures in p
            HomeERAAdvantage = homeERAAdv
            AwayERAAdvantage = awayERAAdv
            HomeWHIPAdvantage = homeWHIPAdv
            AwayWHIPAdvantage = awayWHIPAdv
            HomeStrikeoutRate = homeKRate
            AwayStrikeoutRate = awayKRate
            OPSDifferential = opsDiff
            ERAPlusDifferential = eraPlusDiff
            FIPDifferential = fipDiff
            EloWeatherInteraction = eloWeatherInteraction
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
            let avgTemp = features |> List.averageBy (fun f -> float f.Temperature)
            let avgOPSDiff = features |> List.averageBy (fun f -> float f.OPSDifferential)
            
            sprintf "Feature Statistics:\n- Games: %d\n- Home wins: %d (%.1f%%)\n- Avg Elo diff: %.1f\n- Avg temp (norm): %.2f\n- Avg OPS diff: %.3f"
                count homeWins (float homeWins / float count * 100.0) avgEloDiff avgTemp avgOPSDiff