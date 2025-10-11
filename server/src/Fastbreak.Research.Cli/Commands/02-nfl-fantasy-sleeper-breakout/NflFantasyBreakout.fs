namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO
open Argu

module NflFantasyBreakout =
    let runNflFantasyBreakout (args: ParseResults<'T>) =
        let subcommands = args.GetAllResults()

        if subcommands.IsEmpty then
            printfn "ERROR: A subcommand is required"
            printfn ""
            printfn "Available subcommands:"
            printfn "  verify-data                      Verify data sources are accessible"
            printfn "  train-and-evaluate-algorithms    Train and compare multiple ML algorithms"
            printfn "  predict-weekly-hits              Train model and predict hits for each week"
            printfn "  predict-new-week                 Predict hits for a new week (requires --file and --output)"
            1
        else
            // Check which subcommand was used
            let hasVerifyData = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Verify_Data"))
            let hasTrainAndEvaluate = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Train_And_Evaluate_Algorithms"))
            let hasPredictWeekly = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Predict_Weekly_Hits"))
            let hasPredictNewWeek = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Predict_New_Week"))

            if hasVerifyData then
                DataVerification.verifyData()
            elif hasTrainAndEvaluate then
                EnvConfig.loadEnvFile()
                let dataFolder = Environment.GetEnvironmentVariable("WEEKLY_PLAYER_STATS_DATA_FOLDER")

                if String.IsNullOrEmpty(dataFolder) then
                    printfn "ERROR: WEEKLY_PLAYER_STATS_DATA_FOLDER environment variable is not set"
                    printfn "Please set it in the .env file"
                    1
                elif not (Directory.Exists(dataFolder)) then
                    printfn "ERROR: Data folder does not exist: %s" dataFolder
                    1
                else
                    AlgorithmComparison.runComparison dataFolder
            elif hasPredictWeekly then
                EnvConfig.loadEnvFile()
                let dataFolder = Environment.GetEnvironmentVariable("WEEKLY_PLAYER_STATS_DATA_FOLDER")

                if String.IsNullOrEmpty(dataFolder) then
                    printfn "ERROR: WEEKLY_PLAYER_STATS_DATA_FOLDER environment variable is not set"
                    printfn "Please set it in the .env file"
                    1
                elif not (Directory.Exists(dataFolder)) then
                    printfn "ERROR: Data folder does not exist: %s" dataFolder
                    1
                else
                    WeeklyEvaluator.runWeeklyEvaluation dataFolder
            elif hasPredictNewWeek then
                EnvConfig.loadEnvFile()
                let dataFolder = Environment.GetEnvironmentVariable("WEEKLY_PLAYER_STATS_DATA_FOLDER")

                if String.IsNullOrEmpty(dataFolder) then
                    printfn "ERROR: WEEKLY_PLAYER_STATS_DATA_FOLDER environment variable is not set"
                    printfn "Please set it in the .env file"
                    1
                elif not (Directory.Exists(dataFolder)) then
                    printfn "ERROR: Data folder does not exist: %s" dataFolder
                    1
                else
                    // Extract --file and --output arguments
                    let extractPath (pattern: string) =
                        subcommands
                        |> List.tryPick (fun cmd ->
                            let cmdStr = cmd.ToString()
                            if cmdStr.StartsWith(pattern) then
                                // The format is either "Pattern value" or "Pattern\n  value"
                                // Remove the pattern and trim
                                let afterPattern = cmdStr.Substring(pattern.Length).Trim()
                                // Remove quotes if present
                                let cleaned = afterPattern.Trim('"')
                                Some cleaned
                            else None)

                    let predictionFile = extractPath "Prediction_File"
                    let outputPath = extractPath "Output_Path"

                    match predictionFile, outputPath with
                    | Some file, Some output ->
                        if not (File.Exists(file)) then
                            printfn "ERROR: Prediction file does not exist: %s" file
                            1
                        else
                            PredictNewWeek.runPrediction dataFolder file output
                    | None, _ ->
                        printfn "ERROR: --file argument is required for predict-new-week"
                        printfn "Usage: predict-new-week --file <path-to-csv> --output <output-directory>"
                        1
                    | _, None ->
                        printfn "ERROR: --output argument is required for predict-new-week"
                        printfn "Usage: predict-new-week --file <path-to-csv> --output <output-directory>"
                        1
            else
                printfn "ERROR: Unknown subcommand"
                1
