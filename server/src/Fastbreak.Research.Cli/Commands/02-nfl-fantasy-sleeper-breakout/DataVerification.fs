namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO

module DataVerification =

    let verifyData () =
        EnvConfig.loadEnvFile()

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
