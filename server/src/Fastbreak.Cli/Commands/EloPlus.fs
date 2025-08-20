namespace Fastbreak.Cli.Commands

open System
open Argu
open Fastbreak.Cli.Entities
open Fastbreak.Cli.Services

module EloPlus =
    
    let printGameSummary (game: GameData) =
        let winner = if game.HomeScore > game.AwayScore then game.HomeTeam else game.AwayTeam
        let score = $"{game.HomeScore}-{game.AwayScore}"
        
        printfn "Game: %s vs %s (%s) - Winner: %s" 
            game.AwayTeam game.HomeTeam score winner
        
        match game.Weather with
        | Some weather -> 
            printfn "  Weather: %.1f°F, Wind: %.1f mph %s, Precipitation: %.1f\"" 
                weather.Temperature weather.WindSpeed weather.WindDirection weather.Precipitation
        | None -> printfn "  Weather: No data"
        
        match game.HomePitcher, game.AwayPitcher with
        | Some hp, Some ap ->
            printfn "  Pitchers: %s (ERA: %.2f) vs %s (ERA: %.2f)" 
                hp.Name hp.ERA ap.Name ap.ERA
        | _ -> printfn "  Pitchers: No data"
        
        match game.Metrics with
        | Some metrics ->
            printfn "  Metrics: Home OPS: %.3f, Away OPS: %.3f" 
                metrics.HomeOPS metrics.AwayOPS
        | None -> printfn "  Metrics: No data"
        
        printfn ""
    
    let printError (message: string) =
        Console.ForegroundColor <- ConsoleColor.Red
        printfn "%s" message
        Console.ResetColor()
    
    let loadGameData (filePath: string option) : Result<GameData list * string, string> =
        match filePath with
        | Some path ->
            printfn "Loading games from CSV file: %s" path
            
            match CsvParser.loadGamesFromCsv path with
            | CsvParser.LoadSuccess (games, errorCount) ->
                if errorCount > 0 then
                    printfn "Warning: %d lines failed to parse" errorCount
                printfn "Successfully loaded %d games from CSV" games.Length
                Ok (games, $"csv-file-{System.IO.Path.GetFileNameWithoutExtension(path)}")
            | CsvParser.FileNotFound msg ->
                Error msg
            | CsvParser.FileError msg ->
                Error msg
        | None ->
            printfn "Using sample game data (5 games)"
            Ok (SampleData.sampleGames, "sample-data")
    
    let generateEloPlusRatings (args: ParseResults<'T>) =
        printfn "=== Elo+ Rating Generation ==="
        
        let progressInterval = 10 // Default for now  
        let filePath = 
            try
                // Try to extract file path from args - using string matching for simplicity
                let argStrings = System.Environment.GetCommandLineArgs()
                let fileIndex = Array.tryFindIndex (fun s -> s = "--file" || s = "-f") argStrings
                match fileIndex with
                | Some i when i + 1 < argStrings.Length -> Some argStrings.[i + 1]
                | _ -> None
            with
            | _ -> None
        
        // Load game data from file or use sample data
        match loadGameData filePath with
        | Error errorMsg ->
            printError errorMsg
            1
        | Ok (games, dataSource) ->
            if games.IsEmpty then
                printError "No valid games found in the data source."
                1
            else
                printfn "Processing %d games in chronological order..." games.Length
                printfn "Data source: %s" dataSource
                printfn "Progress reporting: every %d games" progressInterval
                printfn "Using K-Factor: %.0f, Default Rating: %.0f\n" 
                    EloCalculator.K_FACTOR EloCalculator.DEFAULT_RATING
                
                // Sort games chronologically and process with detailed output
                let sortedGames = games |> List.sortBy (fun g -> g.Date)
                let mutable currentRatings = Map.empty<string, decimal>
                
                printfn "Game-by-Game Elo Progression:"
                printfn "============================================"
                
                let startTime = DateTime.Now
                
                sortedGames
                |> List.iteri (fun i game ->
                    let gameDetails = EloCalculator.getGameDetails currentRatings game
                    if i % progressInterval = 0 || i < 10 then // Always show first 10 games
                        printfn "%s" gameDetails
                    elif i % progressInterval = 0 then
                        printfn "Processed %d games..." (i + 1)
                    currentRatings <- EloCalculator.processGame currentRatings game
                )
                
                let endTime = DateTime.Now
                let processingTime = endTime - startTime
                
                // Calculate final ratings
                let finalRatings = EloCalculator.calculateEloRatings games
                let eloRatingRecords = EloCalculator.convertToEloRatings finalRatings DateTime.Now
                
                printfn "\n=== Final Elo Ratings ==="
                printfn "========================="
                
                eloRatingRecords
                |> List.sortByDescending (fun r -> r.StandardElo)
                |> List.sortBy (fun r -> r.Team) // Secondary sort by team name for ties
                |> List.sortByDescending (fun r -> r.StandardElo) // Primary sort by rating
                |> List.iteri (fun i rating ->
                    printfn "%d. %-25s %.3f" (i + 1) rating.Team rating.StandardElo)
                
                printfn "\n=== Elo Rating Statistics ==="
                printfn "============================"
                
                let ratings = eloRatingRecords |> List.map (fun r -> r.StandardElo)
                let avgRating = ratings |> List.average
                let maxRating = ratings |> List.max
                let minRating = ratings |> List.min
                let ratingSpread = maxRating - minRating
                
                printfn "Average Rating: %.3f" avgRating
                printfn "Highest Rating: %.3f" maxRating
                printfn "Lowest Rating:  %.3f" minRating
                printfn "Rating Spread:  %.3f points" ratingSpread
                
                printfn "\n=== Team Performance Summary ==="
                printfn "==============================="
                
                let teamStats = 
                    games
                    |> List.collect (fun g -> 
                        let homeWin = g.HomeScore > g.AwayScore
                        let awayWin = g.AwayScore > g.HomeScore
                        [
                            (g.HomeTeam, if homeWin then (1, 0) elif awayWin then (0, 1) else (0, 0))
                            (g.AwayTeam, if awayWin then (1, 0) elif homeWin then (0, 1) else (0, 0))
                        ])
                    |> List.groupBy fst
                    |> List.map (fun (team, results) ->
                        let wins = results |> List.sumBy (fun (_, (w, _)) -> w)
                        let losses = results |> List.sumBy (fun (_, (_, l)) -> l)
                        let winPct = if (wins + losses) > 0 then float wins / float (wins + losses) else 0.0
                        (team, wins, losses, winPct))
                    |> List.sortByDescending (fun (_, _, _, pct) -> pct)
                
                for (team, wins, losses, winPct) in teamStats do
                    let finalRating = finalRatings.TryFind(team) |> Option.defaultValue EloCalculator.DEFAULT_RATING
                    printfn "%-25s %d-%d (%.3f) | Elo: %.3f" team wins losses winPct finalRating
                
                printfn "\n=== Processing Summary ==="
                printfn "========================="
                printfn "Games processed: %d" games.Length
                printfn "Processing time: %.2f seconds" processingTime.TotalSeconds
                printfn "Games per second: %.1f" (float games.Length / processingTime.TotalSeconds)
                printfn "Data source: %s" dataSource
                
                // Test feature engineering (Phase 8 Step 2)
                printfn "\n=== ML Feature Engineering Test ==="
                printfn "=================================="
                let features = FeatureEngineering.convertGamesToFeatures games
                let featureStats = FeatureEngineering.getFeatureStatistics features
                printfn "%s" featureStats
                
                if features.Length > 0 then
                    let firstFeature = features.[0]
                    printfn "\nSample Feature Vector (Game 1):"
                    printfn "HomeElo: %.1f, AwayElo: %.1f, EloDiff: %.1f" 
                        firstFeature.HomeElo firstFeature.AwayElo firstFeature.EloDifference
                    printfn "Temperature: %.2f, WindSpeed: %.2f, WindFactor: %.2f" 
                        firstFeature.Temperature firstFeature.WindSpeed firstFeature.WindFactor
                    printfn "HomeERA+: %.2f, AwayERA+: %.2f, OPSDiff: %.3f" 
                        firstFeature.HomeERAAdvantage firstFeature.AwayERAAdvantage firstFeature.OPSDifferential
                    printfn "HomeWin: %b" firstFeature.HomeWin
                    
                    // Test ML.NET training (Phase 8 Step 3)
                    if features.Length >= 10 then // Need enough data for training/testing
                        printfn "\n=== ML.NET Model Training Test ==="
                        printfn "================================="
                        let baseline = MLModelTrainer.calculateBaseline features
                        printfn "Baseline accuracy: %.1f%% (always predict majority class)" (baseline * 100.0)
                        
                        try
                            let result = MLModelTrainer.trainModel features MLModelTrainer.defaultConfig
                            printfn "%s" (MLModelTrainer.formatTrainingResult result)
                            
                            if result.Accuracy > baseline then
                                printfn "✅ ML model beats baseline by %.1f%%" ((result.Accuracy - baseline) * 100.0)
                            else
                                printfn "⚠️  ML model underperformed baseline"
                        with
                        | ex -> 
                            printfn "❌ ML training failed: %s" ex.Message
                    else
                        printfn "\n⚠️  Not enough data for ML training (need ≥10 games, have %d)" features.Length
                
                // Test Elo+ calculation (Phase 8 Step 4)
                printfn "\n=== Elo+ Enhanced Ratings Test ==="
                printfn "================================="
                let eloPlusRatings = EloPlusCalculator.calculateEloPlusRatings games EloPlusCalculator.defaultConfig
                let eloPlusStats = EloPlusCalculator.getEloPlusStatistics eloPlusRatings
                printfn "%s" eloPlusStats
                
                if not (Map.isEmpty eloPlusRatings) then
                    printfn "\nTop 10 Elo+ Ratings:"
                    let topRatings = 
                        eloPlusRatings
                        |> Map.toList
                        |> List.map snd
                        |> List.sortByDescending (fun r -> r.FinalEloPlus)
                        |> List.take (min 10 (Map.count eloPlusRatings))
                    
                    topRatings
                    |> List.mapi (fun i rating ->
                        let adjustment = 
                            if rating.EloPlusAdjustment > 0.0 then sprintf "+%.1f" rating.EloPlusAdjustment 
                            elif rating.EloPlusAdjustment < 0.0 then sprintf "%.1f" rating.EloPlusAdjustment
                            else "±0.0"
                        let confidence = 
                            rating.MLConfidence 
                            |> Option.map (fun c -> sprintf " (%.1f%% conf)" (c * 100.0)) 
                            |> Option.defaultValue ""
                        sprintf "%d. %-25s Elo: %.1f → Elo+: %.1f [%s]%s" 
                            (i + 1) rating.Team (float rating.StandardElo) (float rating.FinalEloPlus) adjustment confidence)
                    |> List.iter (printfn "%s")
                else
                    printfn "\n❌ No Elo+ ratings calculated - insufficient game data or ML model training failed"

                0