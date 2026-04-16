module Fastbreak.Daily.V2NarrativeGenerator

open System
open System.Net.Http
open System.Text.Json
open System.Text.Json.Serialization
open Fastbreak.Daily.CallGemini
open Fastbreak.Daily.ChartDownloader

type V2Segment = {
    [<JsonPropertyName("type")>]
    Type: string
    [<JsonPropertyName("value")>]
    Value: string
}

type V2StatRow = {
    [<JsonPropertyName("subject")>]
    Subject: string
    [<JsonPropertyName("stat")>]
    Stat: string
    [<JsonPropertyName("value")>]
    Value: string
}

type V2Topic = {
    [<JsonPropertyName("id")>]
    Id: int
    [<JsonPropertyName("league")>]
    League: string
    [<JsonPropertyName("category")>]
    Category: string
    [<JsonPropertyName("segments")>]
    Segments: V2Segment list
    [<JsonPropertyName("stats")>]
    Stats: V2StatRow list
}

type V2TopicRaw = {
    [<JsonPropertyName("league")>]
    League: string
    [<JsonPropertyName("category")>]
    Category: string
    [<JsonPropertyName("segments")>]
    Segments: V2Segment[]
    [<JsonPropertyName("stats")>]
    Stats: V2StatRow[]
}

type V2Response = {
    [<JsonPropertyName("topics")>]
    Topics: V2TopicRaw[]
}

let private buildV2Prompt (league: string) (chartSummaries: string) =
    let today = DateTime.Now.ToString("MMMM d, yyyy")
    let chartSection =
        if String.IsNullOrWhiteSpace(chartSummaries) then ""
        else "Use this chart data to inform accurate stats:\n" + chartSummaries + "\n"
    String.concat "\n" [
        sprintf "Today is %s. Search for the latest %s news." today league
        ""
        sprintf "Generate 3-4 topic items about current %s events. Each topic should be a single newsworthy item." league
        ""
        "Categories to use: Player Performance, Team Highlight, Transaction, Milestone, Streak, Injury, Draft, Playoffs"
        ""
        "Each topic has:"
        sprintf """- "league": "%s" """ league
        """- "category": one of the categories above"""
        """- "segments": an array of text/link segments that form a paragraph. Use {"type":"link","value":"Player Name"} for player names and team names. Use {"type":"text","value":"regular text"} for everything else. The paragraph should be 2-3 sentences with specific facts, scores, and stats."""
        """- "stats": 3-4 stat rows as {"subject":"ABR","stat":"STAT","value":"123"} where subject is a team abbreviation (e.g. BOS, NYK) or player short name (e.g. SGA, Tatum), stat is a short label (e.g. PTS, REC, FG%, STRK), and value is the number."""
        ""
        chartSection
        "RULES:"
        "- Start segments with a link (player or team name) or with text like \"The \" followed by a team link"
        "- Every player name and team name should be a link segment"
        "- Stats should use real, verifiable numbers from your search or the chart data"
        "- Avoid events older than 2 weeks"
        "- No opinions, only facts and verifiable trends"
        "- Do not include any preamble - respond with JSON only, no markdown"
        ""
        "Respond with ONLY this JSON format:"
        (sprintf "{\"topics\":[{\"league\":\"%s\",\"category\":\"Player Performance\",\"segments\":[{\"type\":\"link\",\"value\":\"Player Name\"},{\"type\":\"text\",\"value\":\" scored 40 points as the \"},{\"type\":\"link\",\"value\":\"Team\"},{\"type\":\"text\",\"value\":\" beat the \"},{\"type\":\"link\",\"value\":\"Other Team\"},{\"type\":\"text\",\"value\":\" 110-98.\"}],\"stats\":[{\"subject\":\"PLR\",\"stat\":\"PTS\",\"value\":\"40\"},{\"subject\":\"TM\",\"stat\":\"REC\",\"value\":\"50-20\"}]}]}" league)
    ]

let private parseV2Response (text: string) : V2TopicRaw list =
    try
        let startIdx = text.IndexOf('{')
        let endIdx = text.LastIndexOf('}')
        if startIdx >= 0 && endIdx > startIdx then
            let jsonPart = text.Substring(startIdx, endIdx - startIdx + 1)
            let cleaned = jsonPart.Replace("```json", "").Replace("```", "").Replace("\\$", "$").Replace("\\'", "'")
            let options = JsonSerializerOptions()
            options.AllowTrailingCommas <- true
            options.ReadCommentHandling <- JsonCommentHandling.Skip
            let response = JsonSerializer.Deserialize<V2Response>(cleaned, options)
            response.Topics |> Array.toList
        else
            printfn "    [V2Parse] No JSON object found in response"
            []
    with ex ->
        printfn "    [V2Parse] Error: %s" ex.Message
        []

let generateV2ForLeague (client: HttpClient) (apiKey: string) (league: string) (charts: ChartData list) = async {
    printfn "  [V2] Generating topics for %s (%d charts)..." league (List.length charts)

    let chartSummaries =
        if List.isEmpty charts then ""
        else
            charts
            |> List.truncate 5
            |> List.map summarizeChartData
            |> String.concat "\n\n---\n\n"

    let prompt = buildV2Prompt league chartSummaries
    let! result = CallGemini.callGeminiWithSearch client apiKey prompt

    printfn "    [V2] Got %d chars, %d links" result.Text.Length result.Links.Length

    let topics = parseV2Response result.Text
    printfn "    [V2] Parsed %d topics for %s" (List.length topics) league
    return topics
}

let generate (charts: ChartData list) = async {
    printfn "[V2] Generating topics..."
    let apiKey = CallGemini.getApiKey()
    use client = new HttpClient()
    client.Timeout <- TimeSpan.FromMinutes(3.0)

    let leagues = ["NBA"; "NHL"; "MLB"; "NFL"]

    let! results =
        leagues
        |> List.map (fun league -> async {
            let leagueCharts = getChartsForLeague league charts
            return! generateV2ForLeague client apiKey league leagueCharts
        })
        |> Async.Sequential

    let allTopics =
        results
        |> Array.toList
        |> List.concat
        |> List.mapi (fun i raw -> {
            Id = i + 1
            League = raw.League
            Category = raw.Category
            Segments = raw.Segments |> Array.toList
            Stats = raw.Stats |> Array.toList
        })

    printfn "[V2] Done! Generated %d topics total" allTopics.Length
    return allTopics
}
