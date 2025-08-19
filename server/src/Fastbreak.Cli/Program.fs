open Argu
open Fastbreak.Cli.Commands

type CliArgs =
    | [<CliPrefix(CliPrefix.None)>] Export_Leaderboard of ParseResults<DataExport.ExportArgs>
    | [<CliPrefix(CliPrefix.None)>] Export_Stats of ParseResults<DataExport.ExportArgs>
    interface IArgParserTemplate with
        member this.Usage =
            match this with
            | Export_Leaderboard _ -> "export leaderboard data"
            | Export_Stats _ -> "export statistics data"

[<EntryPoint>]
let main args =
    let parser = ArgumentParser.Create<CliArgs>(programName = "fastbreak-cli")
    
    try
        let results = parser.ParseCommandLine(inputs = args, raiseOnUsage = true)
        
        match results.GetAllResults() with
        | [Export_Leaderboard exportArgs] -> DataExport.exportLeaderboard exportArgs
        | [Export_Stats exportArgs] -> DataExport.exportStatSheets exportArgs
        | [] -> 
            printfn "%s" (parser.PrintUsage())
            0
        | _ -> 
            printfn "Invalid combination of arguments"
            printfn "%s" (parser.PrintUsage())
            1
    with
    | :? ArguParseException as ex ->
        printfn "%s" ex.Message
        1
