module Fastbreak.Daily.NarrativeGenerator

open System
open System.Net.Http
open System.Text
open System.Text.Json
open System.Text.Json.Serialization
open Fastbreak.Daily.ChartDownloader

// Request types
type Part = {
    [<JsonPropertyName("text")>]
    text: string
}
type Content = {
    [<JsonPropertyName("parts")>]
    parts: Part[]
}
type GoogleSearchTool = {
    [<JsonPropertyName("google_search")>]
    google_search: obj
}
type GenerationConfig = {
    [<JsonPropertyName("temperature")>]
    temperature: float
}
type GeminiRequestWithTools = {
    [<JsonPropertyName("contents")>]
    contents: Content[]
    [<JsonPropertyName("tools")>]
    tools: GoogleSearchTool[]
    [<JsonPropertyName("generationConfig")>]
    generationConfig: GenerationConfig
}
type GeminiRequest = {
    [<JsonPropertyName("contents")>]
    contents: Content[]
    [<JsonPropertyName("generationConfig")>]
    generationConfig: GenerationConfig
}

// Response types
type ResponsePart = {
    [<JsonPropertyName("text")>]
    Text: string
}

type ResponseContent = {
    [<JsonPropertyName("parts")>]
    Parts: ResponsePart[]
}

type GroundingChunk = {
    [<JsonPropertyName("web")>]
    Web: JsonElement option
}

type GroundingMetadata = {
    [<JsonPropertyName("groundingChunks")>]
    GroundingChunks: GroundingChunk[] option
    [<JsonPropertyName("webSearchQueries")>]
    WebSearchQueries: string[] option
}

type Candidate = {
    [<JsonPropertyName("content")>]
    Content: ResponseContent
    [<JsonPropertyName("groundingMetadata")>]
    GroundingMetadata: GroundingMetadata option
}

type GeminiResponse = {
    [<JsonPropertyName("candidates")>]
    Candidates: Candidate[]
}

// Output types
type NarrativeItem = {
    [<JsonPropertyName("title")>]
    Title: string
    [<JsonPropertyName("analysis")>]
    Analysis: string
    [<JsonPropertyName("statisticalContext")>]
    StatisticalContext: string
}

type NarrativeOutput = {
    [<JsonPropertyName("nfl")>]
    Nfl: NarrativeItem[]
    [<JsonPropertyName("nba")>]
    Nba: NarrativeItem[]
    [<JsonPropertyName("nhl")>]
    Nhl: NarrativeItem[]
    [<JsonPropertyName("mlb")>]
    Mlb: NarrativeItem[]
}

// Intermediate types
type InitialNarrativeItem = {
    [<JsonPropertyName("title")>]
    Title: string
    [<JsonPropertyName("analysis")>]
    Analysis: string
}

// Step 1: Get raw grounded text (NOT JSON)
let private buildGroundedPrompt (league: string) =
    let today = DateTime.Now.ToString("MMMM d, yyyy")
    $"""Today is {today}. Search for the latest {league} news from the past week.

Write two paragraphs (2-3 sentences each) covering:
1. Recent game results, scores, and standout performances from this week
2. Current standings, playoff implications, or breaking news (trades, injuries, signings)

Use specific names, dates, scores, and statistics from your search results."""

// Step 2: Structure the grounded text into JSON
let private buildStructurePrompt (league: string) (rawText: string) =
    $"""Convert the following {league} analysis into JSON format.

Raw analysis:
{rawText}

Extract exactly 2 narrative items. Each should have a concise title and the analysis text.

Respond with JSON only, no markdown:
[{{"title": "title here", "analysis": "analysis text here"}}, {{"title": "title here", "analysis": "analysis text here"}}]"""

// Step 3: Add statistical context from chart data
let private buildContextPrompt (league: string) (narrativeItems: InitialNarrativeItem[]) (chartSummaries: string) =
    let narrativesJson = JsonSerializer.Serialize(narrativeItems)
    $"""Add statistical context to these {league} narratives using the chart data.

Narratives:
{narrativesJson}

Chart data for {league}:
{chartSummaries}

For each narrative, add a "statisticalContext" field with 2-3 sentences referencing specific numbers from the charts.

Respond with JSON only, no markdown:
[{{"title": "original title", "analysis": "original analysis", "statisticalContext": "context using chart data"}}, ...]"""

let getApiKey () =
    Environment.GetEnvironmentVariable("GEMINI_API_KEY")
    |> Option.ofObj
    |> Option.defaultWith (fun () -> failwith "GEMINI_API_KEY environment variable not set")

let private callGeminiWithSearch (client: HttpClient) (apiKey: string) (prompt: string) = async {
    let url = $"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={apiKey}"

    // Temperature 1.0 recommended for grounded search per Google docs
    let request: GeminiRequestWithTools = {
        contents = [| { parts = [| { text = prompt } |] } |]
        tools = [| { google_search = obj() } |]
        generationConfig = { temperature = 1.0 }
    }

    let jsonOptions = JsonSerializerOptions()
    jsonOptions.PropertyNamingPolicy <- JsonNamingPolicy.CamelCase

    let json = JsonSerializer.Serialize(request, jsonOptions)
    use content = new StringContent(json, Encoding.UTF8, "application/json")

    let! response = client.PostAsync(url, content) |> Async.AwaitTask
    let! responseBody = response.Content.ReadAsStringAsync() |> Async.AwaitTask

    if not response.IsSuccessStatusCode then
        failwithf "Gemini API error: %s" responseBody

    let geminiResponse = JsonSerializer.Deserialize<GeminiResponse>(responseBody)

    let candidate = geminiResponse.Candidates |> Array.tryHead

    // Check if grounding was actually used
    let hasMetadata =
        candidate
        |> Option.bind (fun c -> c.GroundingMetadata)
        |> Option.isSome

    let searchQueries =
        candidate
        |> Option.bind (fun c -> c.GroundingMetadata)
        |> Option.bind (fun m -> m.WebSearchQueries)
        |> Option.defaultValue [||]

    let wasGrounded = searchQueries.Length > 0

    printfn "    [Grounding metadata: %b, queries: %d]" hasMetadata searchQueries.Length
    if wasGrounded then
        searchQueries |> Array.iter (fun q -> printfn "      Query: %s" q)

    let text =
        candidate
        |> Option.map (fun c -> c.Content.Parts |> Array.map (fun p -> p.Text) |> String.concat "")
        |> Option.defaultValue ""

    return text
}

let private callGemini (client: HttpClient) (apiKey: string) (prompt: string) = async {
    let url = $"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={apiKey}"

    let request: GeminiRequest = {
        contents = [| { parts = [| { text = prompt } |] } |]
        generationConfig = { temperature = 0.2 }
    }

    let jsonOptions = JsonSerializerOptions()
    jsonOptions.PropertyNamingPolicy <- JsonNamingPolicy.CamelCase

    let json = JsonSerializer.Serialize(request, jsonOptions)
    use content = new StringContent(json, Encoding.UTF8, "application/json")

    let! response = client.PostAsync(url, content) |> Async.AwaitTask
    let! responseBody = response.Content.ReadAsStringAsync() |> Async.AwaitTask

    if not response.IsSuccessStatusCode then
        failwithf "Gemini API error: %s" responseBody

    let geminiResponse = JsonSerializer.Deserialize<GeminiResponse>(responseBody)

    return
        geminiResponse.Candidates
        |> Array.tryHead
        |> Option.map (fun c -> c.Content.Parts |> Array.map (fun p -> p.Text) |> String.concat "")
        |> Option.defaultValue ""
}

let private parseJsonArray<'T> (text: string) =
    let startIdx = text.IndexOf('[')
    let endIdx = text.LastIndexOf(']')
    if startIdx >= 0 && endIdx > startIdx then
        let jsonPart = text.Substring(startIdx, endIdx - startIdx + 1)
        let cleaned =
            jsonPart
                .Replace("\\$", "$")
                .Replace("\\'", "'")
        let options = JsonSerializerOptions()
        options.AllowTrailingCommas <- true
        options.ReadCommentHandling <- JsonCommentHandling.Skip
        JsonSerializer.Deserialize<'T[]>(cleaned, options)
    else
        failwithf "Could not find JSON array in response: %s" text

let generateForLeague (client: HttpClient) (apiKey: string) (league: string) (charts: ChartData list) = async {
    printfn "  Generating narratives for %s..." league

    // Step 1: Get grounded raw text (NOT JSON)
    let groundedPrompt = buildGroundedPrompt league
    let! rawText = callGeminiWithSearch client apiKey groundedPrompt
    printfn "    Got grounded text (%d chars)" rawText.Length

    // Step 2: Structure into JSON (non-grounded)
    let structurePrompt = buildStructurePrompt league rawText
    let! structuredResponse = callGemini client apiKey structurePrompt
    let initialNarratives = parseJsonArray<InitialNarrativeItem> structuredResponse
    printfn "    Structured into %d narratives" (Array.length initialNarratives)

    // Step 3: Add statistical context using chart data
    let chartSummaries =
        charts
        |> List.map summarizeChartData
        |> String.concat "\n\n---\n\n"

    if String.IsNullOrWhiteSpace chartSummaries then
        printfn "    No chart data available for %s" league
        return initialNarratives |> Array.map (fun n -> {
            Title = n.Title
            Analysis = n.Analysis
            StatisticalContext = ""
        })
    else
        let contextPrompt = buildContextPrompt league initialNarratives chartSummaries
        let! contextResponse = callGemini client apiKey contextPrompt
        let enrichedNarratives = parseJsonArray<NarrativeItem> contextResponse
        printfn "    Added statistical context"
        return enrichedNarratives
}

let generate (charts: ChartData list) = async {
    printfn "Generating sports narratives..."
    let apiKey = getApiKey()
    use client = new HttpClient()
    client.Timeout <- TimeSpan.FromMinutes(3.0)

    let leagues = ["NFL"; "NBA"; "NHL"; "MLB"]

    let! results =
        leagues
        |> List.map (fun league -> async {
            let leagueCharts = getChartsForLeague league charts
            let! narratives = generateForLeague client apiKey league leagueCharts
            return (league, narratives)
        })
        |> Async.Sequential

    let resultsMap = results |> Array.toList |> Map.ofList

    let output = {
        Nfl = resultsMap |> Map.tryFind "NFL" |> Option.defaultValue [||]
        Nba = resultsMap |> Map.tryFind "NBA" |> Option.defaultValue [||]
        Nhl = resultsMap |> Map.tryFind "NHL" |> Option.defaultValue [||]
        Mlb = resultsMap |> Map.tryFind "MLB" |> Option.defaultValue [||]
    }

    printfn "Done generating narratives!"
    return output
}
