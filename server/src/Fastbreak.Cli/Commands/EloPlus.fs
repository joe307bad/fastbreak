namespace Fastbreak.Cli.Commands

open System
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
    
    let generateEloPlusRatings () =
        printfn "=== Elo+ Rating Generation ==="
        printfn "Phase 3: First-pass Elo calculation implemented\n"
        
        printfn "Processing %d games in chronological order..." SampleData.sampleGames.Length
        printfn "Using K-Factor: %.0f, Default Rating: %.0f\n" 
            EloCalculator.K_FACTOR EloCalculator.DEFAULT_RATING
        
        // Sort games chronologically and process with detailed output
        let sortedGames = SampleData.sampleGames |> List.sortBy (fun g -> g.Date)
        let mutable currentRatings = Map.empty<string, decimal>
        
        printfn "Game-by-Game Elo Progression:"
        printfn "============================================"
        
        for game in sortedGames do
            let gameDetails = EloCalculator.getGameDetails currentRatings game
            printfn "%s" gameDetails
            currentRatings <- EloCalculator.processGame currentRatings game
        
        // Calculate final ratings
        let finalRatings = EloCalculator.calculateEloRatings SampleData.sampleGames
        let eloRatingRecords = EloCalculator.convertToEloRatings finalRatings DateTime.Now
        
        printfn "\n=== Final Elo Ratings ==="
        printfn "========================="
        
        eloRatingRecords
        |> List.sortByDescending (fun r -> r.StandardElo)
        |> List.iteri (fun i rating ->
            printfn "%d. %-25s %.2f" (i + 1) rating.Team rating.StandardElo)
        
        printfn "\n=== Elo Rating Statistics ==="
        printfn "============================"
        
        let ratings = eloRatingRecords |> List.map (fun r -> r.StandardElo)
        let avgRating = ratings |> List.average
        let maxRating = ratings |> List.max
        let minRating = ratings |> List.min
        let ratingSpread = maxRating - minRating
        
        printfn "Average Rating: %.2f" avgRating
        printfn "Highest Rating: %.2f" maxRating
        printfn "Lowest Rating:  %.2f" minRating
        printfn "Rating Spread:  %.2f points" ratingSpread
        
        printfn "\n=== Team Performance Summary ==="
        printfn "==============================="
        
        let teamStats = 
            SampleData.sampleGames
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
            printfn "%-25s %d-%d (%.3f) | Elo: %.2f" team wins losses winPct finalRating
        
        printfn "\nPhase 3 Complete: Traditional Elo ratings calculated"
        printfn "Next Steps:"
        printfn "- Phase 4: Implement second-pass Elo+ calculation with ML.NET"
        printfn "- Phase 5: Add model persistence and rating storage"
        
        0