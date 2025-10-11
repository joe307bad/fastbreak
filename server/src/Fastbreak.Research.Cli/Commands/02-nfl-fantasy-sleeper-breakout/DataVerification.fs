namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO

module DataVerification =

    let verifyData () =
        EnvConfig.loadEnvFile()

        let weeklyStatsFolder = Environment.GetEnvironmentVariable("WEEKLY_PLAYER_STATS_DATA_FOLDER")

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
        if hasErrors then
            printfn "Verification completed with errors"
            1
        else
            printfn "✅ All data sources verified successfully"
            0
