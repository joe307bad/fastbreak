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
        printfn "Phase 4: CSV file input support implemented\n"
        
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
                
                printfn "\nPhase 4 Complete: CSV file input support implemented"
                printfn "Next Steps:"
                printfn "- Phase 5: Create CSV sample data file"
                printfn "- Phase 6: Add streaming and progress reporting"
                printfn "- Phase 7: Implement second-pass Elo+ calculation with ML.NET"
                
                0