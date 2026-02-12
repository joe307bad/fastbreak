open System.IO
open System.Text.Json
open Fastbreak.Daily.NarrativeGenerator

[<EntryPoint>]
let main _ =
    try
        let narratives = generate() |> Async.RunSynchronously

        let options = JsonSerializerOptions(WriteIndented = true)
        let json = JsonSerializer.Serialize(narratives, options)

        File.WriteAllText("narratives.json", json)
        printfn "Wrote narratives.json"
        0
    with
    | ex ->
        eprintfn "Error: %s" ex.Message
        eprintfn "Stack trace: %s" ex.StackTrace
        1
