namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO
open Argu

module NflFantasyBreakout =

    let loadEnvFile () =
        let envPath = Path.Combine(__SOURCE_DIRECTORY__, ".env")
        if File.Exists(envPath) then
            File.ReadAllLines(envPath)
            |> Array.filter (fun line ->
                not (String.IsNullOrWhiteSpace(line)) &&
                not (line.TrimStart().StartsWith("#")) &&
                line.Contains("="))
            |> Array.iter (fun line ->
                let parts = line.Split('=', 2)
                if parts.Length = 2 then
                    Environment.SetEnvironmentVariable(parts.[0].Trim(), parts.[1].Trim()))

    let verifyData () =
        loadEnvFile()

        let weeklyStatsFolder = Environment.GetEnvironmentVariable("WEEKLY_PLAYER_STATS_DATA_FOLDER")
        let defensiveRankingsFile = Environment.GetEnvironmentVariable("DEFENSIVE_POWER_RANKINGS_FILE")

        printfn "Verifying data sources..."
        printfn ""

        let mutable hasErrors = false

        // Verify weekly player stats folder
        if String.IsNullOrEmpty(weeklyStatsFolder) then
            printfn "❌ WEEKLY_PLAYER_STATS_DATA_FOLDER is not set"
            hasErrors <- true
        elif not (Directory.Exists(weeklyStatsFolder)) then
            printfn "❌ WEEKLY_PLAYER_STATS_DATA_FOLDER directory does not exist: %s" weeklyStatsFolder
            hasErrors <- true
        else
            let csvFiles = Directory.GetFiles(weeklyStatsFolder, "*.csv")
            printfn "✅ WEEKLY_PLAYER_STATS_DATA_FOLDER: %s" weeklyStatsFolder
            printfn "   Found %d CSV file(s)" csvFiles.Length
            if csvFiles.Length > 0 then
                csvFiles |> Array.iter (fun file -> printfn "   - %s" (Path.GetFileName(file)))

        printfn ""

        // Verify defensive power rankings file
        if String.IsNullOrEmpty(defensiveRankingsFile) then
            printfn "❌ DEFENSIVE_POWER_RANKINGS_FILE is not set"
            hasErrors <- true
        elif not (File.Exists(defensiveRankingsFile)) then
            printfn "❌ DEFENSIVE_POWER_RANKINGS_FILE does not exist: %s" defensiveRankingsFile
            hasErrors <- true
        elif not (Path.GetExtension(defensiveRankingsFile).ToLower() = ".csv") then
            printfn "❌ DEFENSIVE_POWER_RANKINGS_FILE is not a CSV file: %s" defensiveRankingsFile
            hasErrors <- true
        else
            printfn "✅ DEFENSIVE_POWER_RANKINGS_FILE: %s" defensiveRankingsFile

        printfn ""
        if hasErrors then
            printfn "Verification completed with errors"
            1
        else
            printfn "✅ All data sources verified successfully"
            0

    let calculateSleeperScore () =
        loadEnvFile()

        let weeklyStatsFolder = Environment.GetEnvironmentVariable("WEEKLY_PLAYER_STATS_DATA_FOLDER")
        let defensiveRankingsFile = Environment.GetEnvironmentVariable("DEFENSIVE_POWER_RANKINGS_FILE")

        if String.IsNullOrEmpty(weeklyStatsFolder) || String.IsNullOrEmpty(defensiveRankingsFile) then
            printfn "❌ ERROR: Required environment variables are not set"
            printfn ""
            printfn "Please ensure the following environment variables are configured in .env:"
            if String.IsNullOrEmpty(weeklyStatsFolder) then
                printfn "  - WEEKLY_PLAYER_STATS_DATA_FOLDER"
            if String.IsNullOrEmpty(defensiveRankingsFile) then
                printfn "  - DEFENSIVE_POWER_RANKINGS_FILE"
            printfn ""
            printfn "Run 'dotnet run 02-nfl-fantasy-breakout verify-data' to check your configuration"
            1
        else
            printfn "hello world"
            0

    let runNflFantasyBreakout (args: ParseResults<'T>) =
        let subcommands = args.GetAllResults()

        if subcommands.IsEmpty then
            printfn "ERROR: A subcommand is required"
            printfn ""
            printfn "Available subcommands:"
            printfn "  verify-data              Verify data sources are accessible"
            printfn "  calculate-sleeper-score  Calculate sleeper scores for fantasy players"
            1
        else
            // Match on the subcommand by converting to string
            let subcommandStr = subcommands.[0].ToString()
            if subcommandStr.Contains("Verify_Data") then
                verifyData()
            elif subcommandStr.Contains("Calculate_Sleeper_Score") then
                calculateSleeperScore()
            else
                printfn "ERROR: Unknown subcommand"
                1
