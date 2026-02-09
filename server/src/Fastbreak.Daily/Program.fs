open System.IO

[<EntryPoint>]
let main _ =
    try
        printfn "Generating topics..."
        printfn ""

        let result = TopicsGenerator.generateTopics () |> Async.RunSynchronously

        // Write to output file for debugging
        let outputPath = "topics-output.json"
        File.WriteAllText(outputPath, result)

        printfn "Raw response:"
        printfn "%s" result
        printfn ""
        printfn "Output written to: %s" outputPath

        0
    with
    | ex ->
        eprintfn "Error: %s" ex.Message
        1
