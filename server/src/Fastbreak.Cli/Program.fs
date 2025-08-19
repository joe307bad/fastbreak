open Argu
open Fastbreak.Cli.Commands

type CliArgs =
    | [<CliPrefix(CliPrefix.None)>] Generate_Elo_Plus
    interface IArgParserTemplate with
        member this.Usage =
            match this with
            | Generate_Elo_Plus -> "generate Elo+ ratings using ML.NET"

[<EntryPoint>]
let main args =
    let parser = ArgumentParser.Create<CliArgs>(programName = "fastbreak-cli")
    
    try
        let results = parser.ParseCommandLine(inputs = args, raiseOnUsage = true)
        
        match results.GetAllResults() with
        | [Generate_Elo_Plus] -> EloPlus.generateEloPlusRatings ()
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
