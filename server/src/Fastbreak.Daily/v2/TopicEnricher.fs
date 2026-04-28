module Fastbreak.Daily.V2.TopicEnricher

open System
open System.IO
open System.Net.Http
open System.Text.Json
open System.Text.Json.Serialization
open System.Text.RegularExpressions
open Fastbreak.Daily.CallGemini
open Fastbreak.Daily.TextSegmentConverter
open Fastbreak.Daily.V2.TopicGenerator

[<CLIMutable>]
type EnrichedTopic = {
    [<JsonPropertyName("league")>]
    League: string
    [<JsonPropertyName("category")>]
    Category: string
    [<JsonPropertyName("summary")>]
    Summary: string
    [<JsonPropertyName("summarySegments")>]
    SummarySegments: TextSegment list
    [<JsonPropertyName("teams")>]
    Teams: string list
    [<JsonPropertyName("players")>]
    Players: string list
}

let private teamAbbrevRegex = Regex(@"/team/_/name/([^/]+)", RegexOptions.Compiled)

let private extractTeamAbbrev (url: string) : string option =
    if isNull url then None
    else
        let m = teamAbbrevRegex.Match(url)
        if m.Success then Some (m.Groups.[1].Value.ToUpperInvariant())
        else None

let private isPlayerUrl (url: string) =
    not (isNull url) && url.Contains("wikipedia.org/wiki/")

let private extractTeamsAndPlayers (segments: TextSegment list) =
    let teams = ResizeArray<string>()
    let players = ResizeArray<string>()
    for seg in segments do
        if seg.Type = "link" && not (isNull seg.Url) then
            match extractTeamAbbrev seg.Url with
            | Some abbrev ->
                if not (teams.Contains abbrev) then teams.Add(abbrev)
            | None ->
                if isPlayerUrl seg.Url && not (isNull seg.Value) then
                    let name = seg.Value.Trim()
                    if name.Length > 0 && not (players.Contains name) then
                        players.Add(name)
    List.ofSeq teams, List.ofSeq players

// In-memory pipeline stage: enrich each topic with text segments + extracted teams/players.
let enrichTopicsCore (topics: GeneratedTopic list) = async {
    let total = List.length topics
    printfn "[TopicEnricher] enriching %d topics" total

    let apiKey = getApiKey()
    use client = new HttpClient()
    client.Timeout <- TimeSpan.FromMinutes(5.0)

    let enriched = ResizeArray<EnrichedTopic>()

    for i, t in List.indexed topics do
        printfn ""
        printfn "[TopicEnricher] (%d/%d) %s / %s" (i + 1) total t.League t.Category

        let! segments = convertToSegments client apiKey t.League t.Summary
        let teams, players = extractTeamsAndPlayers segments

        printfn "    segments=%d teams=%d players=%d"
            (List.length segments) (List.length teams) (List.length players)
        if not (List.isEmpty teams) then
            printfn "      teams: %s" (String.concat ", " teams)
        if not (List.isEmpty players) then
            printfn "      players: %s" (String.concat ", " players)

        enriched.Add(
            { League = t.League
              Category = t.Category
              Summary = t.Summary
              SummarySegments = segments
              Teams = teams
              Players = players })

    return List.ofSeq enriched
}

// File-based wrapper for the standalone `enrich-topics` CLI subcommand.
let enrichTopics (topicsJsonPath: string) = async {
    if not (File.Exists topicsJsonPath) then
        failwithf "topics JSON file not found: %s" topicsJsonPath

    let inputJson = File.ReadAllText(topicsJsonPath)
    let readOptions = JsonSerializerOptions()
    readOptions.AllowTrailingCommas <- true
    readOptions.ReadCommentHandling <- JsonCommentHandling.Skip
    let topics =
        JsonSerializer.Deserialize<GeneratedTopic[]>(inputJson, readOptions)
        |> Array.toList

    printfn "[TopicEnricher] loaded %d topics from %s" (List.length topics) topicsJsonPath

    let! enriched = enrichTopicsCore topics

    let outputDir = Path.GetDirectoryName(Path.GetFullPath topicsJsonPath)
    let outputPath = Path.Combine(outputDir, "topics-enriched.json")
    let writeOptions = JsonSerializerOptions(WriteIndented = true)
    let json = JsonSerializer.Serialize(enriched, writeOptions)
    File.WriteAllText(outputPath, json)

    printfn ""
    printfn "[TopicEnricher] wrote %d enriched topics to %s" (List.length enriched) outputPath

    return List.length enriched
}
