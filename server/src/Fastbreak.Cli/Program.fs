open Argu
open Fastbreak.Cli.Commands

type GenerateEloPlusArgs =
    | [<AltCommandLine("-f")>] File of path:string
    | [<AltCommandLine("-p")>] Progress of interval:int
    interface IArgParserTemplate with
        member this.Usage =
            match this with
            | File _ -> "CSV file path for game data (optional, uses sample data if not specified)"
            | Progress _ -> "report progress every N lines (default: 10)"

type CliArgs =
    | [<CliPrefix(CliPrefix.None)>] Generate_Elo_Plus of ParseResults<GenerateEloPlusArgs>
    interface IArgParserTemplate with
        member this.Usage =
            match this with
            | Generate_Elo_Plus _ -> "generate Elo+ ratings using ML.NET"

[<EntryPoint>]
let main args =
    let parser = ArgumentParser.Create<CliArgs>(programName = "fastbreak-cli")
    
    try
        let results = parser.ParseCommandLine(inputs = args, raiseOnUsage = true)
        
        match results.GetAllResults() with
        | [Generate_Elo_Plus subArgs] -> EloPlus.generateEloPlusRatings subArgs
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
