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
            1
        else
            // Check which subcommand was used
            let hasVerifyData = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Verify_Data"))

            if hasVerifyData then
                DataVerification.verifyData()
            else
                printfn "ERROR: Unknown subcommand"
                1
