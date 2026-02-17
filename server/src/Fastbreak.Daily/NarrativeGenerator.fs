module Fastbreak.Daily.NarrativeGenerator

open System
open System.Net.Http
open System.Text
open System.Text.Json
open System.Text.Json.Serialization
open Fastbreak.Daily.ChartDownloader
open Fastbreak.Daily.CallGemini

// Re-export RelevantLink for use by other modules
type RelevantLink = CallGemini.RelevantLink

type DataPoint = {
    [<JsonPropertyName("metric")>]
    Metric: string
    [<JsonPropertyName("value")>]
    Value: string
    [<JsonPropertyName("chartName")>]
    ChartName: string
    [<JsonPropertyName("team")>]
    Team: string
    [<JsonPropertyName("player")>]
    Player: string
    [<JsonPropertyName("id")>]
    Id: string
    [<JsonPropertyName("vizType")>]
    VizType: string
}

type Narrative = {
    [<JsonPropertyName("title")>]
    Title: string
    [<JsonPropertyName("summary")>]
    Summary: string
    [<JsonPropertyName("league")>]
    League: string
    [<JsonPropertyName("links")>]
    Links: RelevantLink list
    [<JsonPropertyName("dataPoints")>]
    DataPoints: DataPoint list
    [<JsonPropertyName("statisticalContext")>]
    StatisticalContext: string
}

// Intermediate types
type InitialNarrativeItem = {
    [<JsonPropertyName("title")>]
    Title: string
    [<JsonPropertyName("summary")>]
    Summary: string
}

type DataPointsResponse = {
    [<JsonPropertyName("dataPoints")>]
    DataPoints: DataPoint[]
}

// Topic prompts - each gets its own grounded search
let private buildTopicPrompt (league: string) (topic: string) =
    let today = DateTime.Now.ToString("MMMM d, yyyy")
    $"""Today is {today}. Search for the latest {league} news about: {topic}

Write one paragraph (2-3 sentences) with specific facts, names, dates, scores and statistics from your search results. Prioritize relevance, current events, future analysis. Make the narrative hard hitting about data and results.

IMPORTANT: Start directly with the content. Do not include any preamble like "Okay, I will search" or "Here is" - just write the paragraph immediately."""

// Structure a single narrative from grounded text
let private buildStructurePrompt (league: string) (topic: string) (rawText: string) =
    $"""Convert this {league} analysis into JSON.

Topic focus: {topic}
Analysis: {rawText}

Create a specific, unique title that captures the main story from this analysis. The title should:
- Be specific to what's covered (mention team names, player names, or specific events)
- NOT be generic like "NBA News Update" or "Latest {league} News"
- Reflect the topic focus ({topic})

Keep the summary text as provided.

Respond with JSON only, no markdown:
{{"title": "specific title about the main story", "summary": "the analysis text"}}"""

// Step 3: Extract relevant data points from chart data
let private buildDataPointsPrompt (league: string) (narrativeTitle: string) (narrativeSummary: string) (chartSummaries: string) =
    $"""Given this {league} narrative and chart data, extract 3-5 relevant data points.

Narrative:
Title: {narrativeTitle}
Summary: {narrativeSummary}

IMPORTANT: Look for any team names or player names mentioned in the narrative. Teams are often abbreviated to 3 letters (e.g., NYK, LAL, BOS, CHI, KC, PHI, DET, SEA, NE, etc.). Find data points for these specific teams/players from the charts.

Chart data:
{chartSummaries}

Find exactly 5 specific metrics and values from the a mix of visualization types/charts that are relevant to this narrative. For each data point:
- Include the 3-letter team abbreviation in "team"
- If the data point is about a specific player, include their name in "player" (otherwise leave empty string)
- Include the chart id in "id"
- Include the visualization type in "vizType" (one of: SCATTER_PLOT, BAR_GRAPH, LINE_CHART, TABLE)

Respond with JSON only, no markdown:
{{"dataPoints": [{{"metric": "metric name", "value": "value with units", "chartName": "name of chart", "team": "ABC", "player": "", "id": "chart_id", "vizType": "SCATTER_PLOT"}}, ...]}}"""

// Step 4: Generate statistical context prose from data points
let private buildStatisticalContextPrompt (league: string) (narrativeTitle: string) (narrativeSummary: string) (dataPoints: DataPoint list) =
    let dataPointsText =
        dataPoints
        |> List.map (fun dp -> $"- {dp.Team}: {dp.Metric} = {dp.Value}")
        |> String.concat "\n"
    $"""Given this {league} narrative and its supporting data points, write a brief 2-3 sentence statistical context that references specific teams and their statistics.

Narrative:
Title: {narrativeTitle}
Summary: {narrativeSummary}

Data Points:
{dataPointsText}

IMPORTANT REQUIREMENTS:
1. You MUST mention specific team names (use full names like "the Celtics" or "Boston" not abbreviations)
2. You MUST include actual numbers and statistics from the data points
3. Try to connect stats to the narrative but don't try to stretch the analysis too much. We don't want to be too verbose.
5. Read the description of the chart, it has important context for the stats. For instance, for some stats, lower means better
6. When mentioning stats, mention the team's positioning across the league. e.g. Team X is in the top third/bottom quarter in stat Y

Example good output: "The Celtics lead the league with a 45-12 record and rank first in offensive rating at 118.2, while the Lakers sit at 32-25 with the 8th-ranked defense."

Start directly with the content. No preamble."""

let getApiKey () = CallGemini.getApiKey()

let private callGemini (client: HttpClient) (apiKey: string) (prompt: string) =
    CallGemini.callGeminiOrFail client apiKey prompt 0.2

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

let private parseJson (text: string) : InitialNarrativeItem =
    let startIdx = text.IndexOf('{')
    let endIdx = text.LastIndexOf('}')
    if startIdx >= 0 && endIdx > startIdx then
        let jsonPart = text.Substring(startIdx, endIdx - startIdx + 1)
        let cleaned = jsonPart.Replace("\\$", "$").Replace("\\'", "'")
        let options = JsonSerializerOptions()
        options.AllowTrailingCommas <- true
        JsonSerializer.Deserialize<InitialNarrativeItem>(cleaned, options)
    else
        { Title = "News Update"; Summary = text }

let private parseDataPoints (text: string) =
    let options = JsonSerializerOptions()
    options.AllowTrailingCommas <- true

    // First, try to find a JSON array (Gemini sometimes returns bare arrays)
    let arrayStartIdx = text.IndexOf('[')
    let arrayEndIdx = text.LastIndexOf(']')

    if arrayStartIdx >= 0 && arrayEndIdx > arrayStartIdx then
        let jsonPart = text.Substring(arrayStartIdx, arrayEndIdx - arrayStartIdx + 1)
        let cleaned = jsonPart.Replace("\\$", "$").Replace("\\'", "'")
        try
            // Try parsing as a bare array of DataPoints
            let dataPoints = JsonSerializer.Deserialize<DataPoint[]>(cleaned, options)
            printfn "      [ParseDataPoints] Parsed as array: %d items" dataPoints.Length
            dataPoints |> Array.toList
        with ex ->
            printfn "      [ParseDataPoints] Array parse failed: %s" ex.Message
            // Fall back to trying object format
            let objStartIdx = text.IndexOf('{')
            let objEndIdx = text.LastIndexOf('}')
            if objStartIdx >= 0 && objEndIdx > objStartIdx then
                let objJsonPart = text.Substring(objStartIdx, objEndIdx - objStartIdx + 1)
                let objCleaned = objJsonPart.Replace("\\$", "$").Replace("\\'", "'")
                try
                    let response = JsonSerializer.Deserialize<DataPointsResponse>(objCleaned, options)
                    printfn "      [ParseDataPoints] Parsed as object: %d items" response.DataPoints.Length
                    response.DataPoints |> Array.toList
                with ex2 ->
                    printfn "      [ParseDataPoints] Object parse also failed: %s" ex2.Message
                    printfn "      [ParseDataPoints] JSON attempted: %s..." (objCleaned.Substring(0, min 300 objCleaned.Length))
                    []
            else
                []
    else
        // No array found - Gemini sometimes returns comma-separated objects without brackets
        let objStartIdx = text.IndexOf('{')
        let objEndIdx = text.LastIndexOf('}')
        if objStartIdx >= 0 && objEndIdx > objStartIdx then
            let jsonPart = text.Substring(objStartIdx, objEndIdx - objStartIdx + 1)
            let cleaned = jsonPart.Replace("\\$", "$").Replace("\\'", "'")
            // First, try wrapping in array brackets (handles comma-separated objects)
            try
                let asArray = "[" + cleaned + "]"
                let dataPoints = JsonSerializer.Deserialize<DataPoint[]>(asArray, options)
                printfn "      [ParseDataPoints] Parsed as wrapped array: %d items" dataPoints.Length
                dataPoints |> Array.toList
            with _ ->
                // Fall back to trying as DataPointsResponse object
                try
                    let response = JsonSerializer.Deserialize<DataPointsResponse>(cleaned, options)
                    printfn "      [ParseDataPoints] Parsed as object: %d items" response.DataPoints.Length
                    response.DataPoints |> Array.toList
                with ex ->
                    printfn "      [ParseDataPoints] JSON parse failed: %s" ex.Message
                    printfn "      [ParseDataPoints] JSON attempted: %s..." (cleaned.Substring(0, min 300 cleaned.Length))
                    []
        else
            printfn "      [ParseDataPoints] No JSON found in response"
            []

// Topics for each league - each becomes a separate narrative with its own grounded search
let private getTopics (league: string) =
    match league with
    | "CBB" -> ["game results, scores, and standout player performances from recent games"; "roster moves, trades, injuries, and tournament implications"]
    | "NFL" -> ["game results, scores, and standout player performances from recent games"; "roster moves, trades, injuries, and playoff implications"]
    | "NBA" -> ["game results, scores, and standout player performances from recent games"; "roster moves, trades, injuries, and playoff race standings"]
    | "NHL" -> ["game results, scores, and standout player performances from recent games"; "roster moves, trades, injuries, and playoff race standings"]
    | "MLB" -> ["game results, scores, and standout player performances from recent games"; "roster moves, trades, injuries, and division race standings"]
    | _ -> ["recent news"; "upcoming events"]

let private generateNarrative (client: HttpClient) (apiKey: string) (league: string) (topic: string) (chartSummaries: string) = async {
    // Step 1: Get grounded search for this specific topic
    let groundedPrompt = buildTopicPrompt league topic
    let! groundedResult = CallGemini.callGeminiWithSearch client apiKey groundedPrompt
    printfn "    [%s] Got %d chars, %d links" topic groundedResult.Text.Length groundedResult.Links.Length

    // Step 2: Structure into title + summary
    let structurePrompt = buildStructurePrompt league topic groundedResult.Text
    let! structuredResponse = callGemini client apiKey structurePrompt
    let parsed = parseJson structuredResponse

    // Step 3: Extract data points from charts
    let! dataPoints = async {
        if String.IsNullOrWhiteSpace chartSummaries then
            printfn "      [DataPoints] No chart summaries available"
            return []
        else
            printfn "      [DataPoints] Chart summaries size: %d chars" chartSummaries.Length
            let dpPrompt = buildDataPointsPrompt league parsed.Title parsed.Summary chartSummaries
            printfn "      [DataPoints] Full prompt size: %d chars" dpPrompt.Length
            let! dpResponse = callGemini client apiKey dpPrompt
            printfn "      [DataPoints] Gemini response size: %d chars" dpResponse.Length
            let parsed = parseDataPoints dpResponse
            printfn "      [DataPoints] Parsed %d data points" (List.length parsed)
            return parsed
    }

    // Step 4: Generate statistical context from data points (only if we have chart data)
    let! statisticalContext = async {
        if List.isEmpty dataPoints then
            return ""
        else
            let contextPrompt = buildStatisticalContextPrompt league parsed.Title parsed.Summary dataPoints
            let! contextResponse = callGemini client apiKey contextPrompt
            return contextResponse.Trim()
    }

    return {
        Title = parsed.Title
        Summary = parsed.Summary
        League = league
        Links = groundedResult.Links
        DataPoints = dataPoints
        StatisticalContext = statisticalContext
    }
}

let generateForLeague (client: HttpClient) (apiKey: string) (league: string) (charts: ChartData list) = async {
    printfn "  Generating narratives for %s (%d charts available)..." league (List.length charts)
    if List.isEmpty charts then
        printfn "    WARNING: No charts found for %s - data points will be empty" league

    let chartSummaries =
        charts
        |> List.map summarizeChartData
        |> String.concat "\n\n---\n\n"

    let topics = getTopics league

    let! narratives =
        topics
        |> List.map (fun topic -> generateNarrative client apiKey league topic chartSummaries)
        |> Async.Sequential

    printfn "    Done with %s" league
    return narratives |> Array.toList
}

// Simple deduplication - remove narratives with very similar titles
let private deduplicateNarratives (narratives: Narrative list) =
    let normalize (s: string) =
        s.ToLowerInvariant()
            .Replace("the ", "")
            .Replace("a ", "")
            .Trim()

    let isSimilar (a: string) (b: string) =
        let na = normalize a
        let nb = normalize b
        na = nb || na.Contains(nb) || nb.Contains(na)

    narratives
    |> List.fold (fun acc narrative ->
        let isDuplicate =
            acc |> List.exists (fun existing ->
                existing.League = narrative.League && isSimilar existing.Title narrative.Title)
        if isDuplicate then
            printfn "    Removing duplicate: %s" narrative.Title
            acc
        else
            narrative :: acc
    ) []
    |> List.rev

let generate (charts: ChartData list) = async {
    printfn "Generating sports narratives..."
    let apiKey = getApiKey()
    use client = new HttpClient()
    client.Timeout <- TimeSpan.FromMinutes(3.0)

    let leagues = ["NBA"; "NHL"; "MLB"; "NFL"]

    let! results =
        leagues
        |> List.map (fun league -> async {
            let leagueCharts = getChartsForLeague league charts
            let! narratives = generateForLeague client apiKey league leagueCharts
            return narratives
        })
        |> Async.Sequential

    let allNarratives = results |> Array.toList |> List.concat
    let deduplicated = deduplicateNarratives allNarratives

    printfn "Done generating narratives! Total: %d (removed %d duplicates)" deduplicated.Length (allNarratives.Length - deduplicated.Length)
    return deduplicated
}
