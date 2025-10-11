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
            1
        else
            // Check which subcommand was used
            let hasVerifyData = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Verify_Data"))
            let hasTrainAndEvaluate = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Train_And_Evaluate_Algorithms"))

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
            else
                printfn "ERROR: Unknown subcommand"
                1
