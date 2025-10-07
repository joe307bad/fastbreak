namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO
open Argu

module NflFantasyBreakout =

    let calculateSleeperScore (playersFile: string) (pngOutputPath: string option) =
        EnvConfig.loadEnvFile()

        let defensiveRankingsFile = Environment.GetEnvironmentVariable("DEFENSIVE_POWER_RANKINGS_FILE")
        let weeklyStatsFolder = Environment.GetEnvironmentVariable("WEEKLY_PLAYER_STATS_DATA_FOLDER")

        if String.IsNullOrEmpty(defensiveRankingsFile) then
            printfn "❌ ERROR: Required environment variable DEFENSIVE_POWER_RANKINGS_FILE is not set"
            printfn ""
            printfn "Run 'dotnet run 02-nfl-fantasy-breakout verify-data' to check your configuration"
            1
        else
            // Prepend WEEKLY_PLAYER_STATS_DATA_FOLDER if playersFile is just a filename
            let fullPlayersPath =
                if Path.IsPathRooted(playersFile) then
                    playersFile
                elif not (String.IsNullOrEmpty(weeklyStatsFolder)) then
                    Path.Combine(weeklyStatsFolder, playersFile)
                else
                    playersFile

            printfn "Loading player data from: %s" fullPlayersPath
            let players = SleeperScoreCalculator.parsePlayersCsv fullPlayersPath

            printfn "Loading defense rankings from: %s" defensiveRankingsFile
            let defenseRankings = SleeperScoreCalculator.parseDefenseRankingsCsv defensiveRankingsFile
            let defenseMap = defenseRankings |> List.map (fun d -> d.Team, d) |> Map.ofList

            printfn ""
            printfn "Calculating sleeper scores for %d players..." players.Length
            printfn ""

            let sleeperScores = SleeperScoreCalculator.calculateSleeperScores players defenseMap

            ConsoleOutputFormatter.printSleeperScores sleeperScores

            printfn ""
            printfn "✅ Calculated sleeper scores for %d players" sleeperScores.Length

            // Generate HTML table if requested
            match pngOutputPath with
            | Some path ->
                try
                    printfn ""
                    printfn "Generating HTML table..."
                    let savedPath = HtmlOutputGenerator.generateSleeperScoreTable sleeperScores path
                    printfn "✅ HTML table saved to: %s" savedPath
                with
                | ex ->
                    printfn "❌ Error generating HTML: %s" ex.Message
            | None -> ()

            0

    let runNflFantasyBreakout (args: ParseResults<'T>) =
        let subcommands = args.GetAllResults()

        if subcommands.IsEmpty then
            printfn "ERROR: A subcommand is required"
            printfn ""
            printfn "Available subcommands:"
            printfn "  verify-data                      Verify data sources are accessible"
            printfn "  calculate-sleeper-score --players <csv_path>  Calculate sleeper scores for fantasy players"
            1
        else
            // Check which subcommand was used
            let hasVerifyData = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Verify_Data"))
            let hasCalculateScore = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Calculate_Sleeper_Score"))

            if hasVerifyData then
                DataVerification.verifyData()
            elif hasCalculateScore then
                // Extract the players file path and png output path from command line args
                let argStrings = Environment.GetCommandLineArgs()
                let playersIndex = Array.tryFindIndex (fun s -> s = "--players") argStrings
                let pngIndex = Array.tryFindIndex (fun (s: string) -> s.StartsWith("--png")) argStrings

                let pngPath =
                    match pngIndex with
                    | Some i ->
                        // Handle --png=path or --png path
                        if argStrings.[i].Contains("=") then
                            Some ((argStrings.[i].Split('=') : string array).[1])
                        elif i + 1 < argStrings.Length then
                            Some argStrings.[i + 1]
                        else
                            None
                    | None -> None

                match playersIndex with
                | Some i when i + 1 < argStrings.Length ->
                    let playersFile = argStrings.[i + 1]
                    calculateSleeperScore playersFile pngPath
                | _ ->
                    printfn "ERROR: --players argument is required for calculate-sleeper-score"
                    printfn "Usage: dotnet run 02-nfl-fantasy-breakout calculate-sleeper-score --players <csv_path> [--png <output_dir>]"
                    1
            else
                printfn "ERROR: Unknown subcommand"
                1
