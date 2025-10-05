open Argu
open Fastbreak.Research.Cli.Commands.GenerateEloPlus
open Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

type GenerateEloPlusArgs =
    | [<AltCommandLine("-f"); Mandatory>] File of path:string
    | [<AltCommandLine("-p")>] Progress of interval:int
    | [<AltCommandLine("-m")>] Markdown of path:string
    interface IArgParserTemplate with
        member this.Usage =
            match this with
            | File _ -> "CSV file path for game data (required)"
            | Progress _ -> "report progress every N lines (default: 10)"
            | Markdown _ -> "output markdown report to specified file path"

type NflFantasyBreakoutArgs =
    | [<CliPrefix(CliPrefix.None)>] Verify_Data
    | [<CliPrefix(CliPrefix.None)>] Calculate_Sleeper_Score
    interface IArgParserTemplate with
        member this.Usage =
            match this with
            | Verify_Data -> "verify data sources are accessible"
            | Calculate_Sleeper_Score -> "calculate sleeper scores for fantasy players"

type CliArgs =
    | [<CustomCommandLine("01-generate-elo-plus"); CliPrefix(CliPrefix.None)>] Generate_Elo_Plus_01 of ParseResults<GenerateEloPlusArgs>
    | [<CustomCommandLine("02-nfl-fantasy-breakout"); CliPrefix(CliPrefix.None)>] Nfl_Fantasy_Breakout of ParseResults<NflFantasyBreakoutArgs>
    | Version
    interface IArgParserTemplate with
        member this.Usage =
            match this with
            | Generate_Elo_Plus_01 _ -> "generate Elo+ ratings using ML.NET"
            | Nfl_Fantasy_Breakout _ -> "NFL Fantasy Breakout Prediction"
            | Version -> "display version information"

[<EntryPoint>]
let main args =
    let parser = ArgumentParser.Create<CliArgs>(programName = "fastbreak-research")
    
    try
        let results = parser.ParseCommandLine(inputs = args, raiseOnUsage = true)
        
        match results.GetAllResults() with
        | [Generate_Elo_Plus_01 subArgs] -> EloPlus.generateEloPlusRatings subArgs
        | [Nfl_Fantasy_Breakout subArgs] -> NflFantasyBreakout.runNflFantasyBreakout subArgs
        | [Version] -> 
            printfn "Fastbreak Research CLI v0.1.0"
            0
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