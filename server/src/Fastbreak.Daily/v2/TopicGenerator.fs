module Fastbreak.Daily.V2.TopicGenerator

open System
open System.IO
open System.Net.Http
open System.Text.Json
open System.Text.Json.Serialization
open Fastbreak.Daily.CallGemini
open Fastbreak.Daily.V2.PromptBuilder

type GeneratedTopic = {
    [<JsonPropertyName("league")>]
    League: string
    [<JsonPropertyName("summary")>]
    Summary: string
    [<JsonPropertyName("category")>]
    Category: string
}

let private extractJsonArray (text: string) =
    let startIdx = text.IndexOf('[')
    let endIdx = text.LastIndexOf(']')
    if startIdx >= 0 && endIdx > startIdx then
        Some (text.Substring(startIdx, endIdx - startIdx + 1))
    else
        None

let private tryParseArray (text: string) : GeneratedTopic list =
    match extractJsonArray text with
    | None -> []
    | Some json ->
        try
            let options = JsonSerializerOptions()
            options.AllowTrailingCommas <- true
            options.ReadCommentHandling <- JsonCommentHandling.Skip
            JsonSerializer.Deserialize<GeneratedTopic[]>(json, options)
            |> Array.toList
        with ex ->
            printfn "    [TopicGenerator] JSON parse failed: %s" ex.Message
            []

// In-memory pipeline stage: call Gemini for each prompt and return the parsed flat list.
let generateTopicsCore (prompts: Prompt list) = async {
    let apiKey = getApiKey()
    use client = new HttpClient()
    client.Timeout <- TimeSpan.FromMinutes(5.0)

    let total = List.length prompts
    let results = ResizeArray<GeneratedTopic>()

    for i, prompt in List.indexed prompts do
        printfn ""
        printfn "[TopicGenerator] (%d/%d) %s" (i + 1) total prompt.League

        try
            let! response = callGeminiWithSearch client apiKey prompt.Text
            let parsed = tryParseArray response.Text

            // Restore league/category from the prompt itself: Gemini occasionally
            // drops the category field even though the prompt asks for it (we've
            // seen this happen for NBA topics while NHL/MLB on the same call
            // round came back fine). The bullets in prompt.Text and the parsed
            // response are in the same order, so we can pair them up by index
            // and overwrite missing/null values.
            let categories = prompt.Categories
            let restored =
                parsed
                |> List.mapi (fun idx t ->
                    let cat =
                        if isNull t.Category || t.Category.Trim() = "" then
                            categories |> List.tryItem idx |> Option.defaultValue t.Category
                        else
                            t.Category
                    let lg =
                        if isNull t.League || t.League.Trim() = "" then prompt.League
                        else t.League
                    { t with League = lg; Category = cat })

            results.AddRange(restored)
            printfn "    [TopicGenerator] parsed %d summaries" (List.length restored)
            for t in restored do
                printfn "      - [%s] %s" t.Category t.Summary
        with ex ->
            printfn "    [TopicGenerator] call failed: %s" ex.Message

        if i < total - 1 then
            do! Async.Sleep 3000

    return List.ofSeq results
}

// File-based wrapper for the standalone `--v2` CLI subcommand.
let generateTopics (outputDir: string) (prompts: Prompt list) = async {
    let! ordered = generateTopicsCore prompts

    Directory.CreateDirectory(outputDir) |> ignore
    let outputPath = Path.Combine(outputDir, "topics.json")
    let writeOptions = JsonSerializerOptions(WriteIndented = true)
    let json = JsonSerializer.Serialize(ordered, writeOptions)
    File.WriteAllText(outputPath, json)

    printfn ""
    printfn "[TopicGenerator] wrote %d topics to %s" (List.length ordered) outputPath

    return ordered
}
