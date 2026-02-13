module Fastbreak.Daily.TextSegmentConverter

open System
open System.Net.Http
open System.Text.Json
open System.Text.Json.Serialization
open Fastbreak.Daily.CallGemini
open Fastbreak.Daily.NarrativeGenerator

// Text segment types - using nullable string instead of option for JSON compat
[<CLIMutable>]
type TextSegment = {
    [<JsonPropertyName("type")>]
    Type: string  // "text" or "link"
    [<JsonPropertyName("value")>]
    Value: string
    [<JsonPropertyName("url")>]
    Url: string  // null for text segments
}

[<CLIMutable>]
type TextSegmentsResponse = {
    [<JsonPropertyName("segments")>]
    Segments: TextSegment[]
}

let private buildSegmentPrompt (text: string) (league: string) =
    let leagueTeamUrlPattern =
        match league.ToUpperInvariant() with
        | "NBA" -> "https://www.espn.com/nba/team/_/name/{team_abbr}"
        | "NFL" -> "https://www.espn.com/nfl/team/_/name/{team_abbr}"
        | "NHL" -> "https://www.espn.com/nhl/team/_/name/{team_abbr}"
        | "MLB" -> "https://www.espn.com/mlb/team/_/name/{team_abbr}"
        | _ -> "https://www.espn.com/{league}/team/_/name/{team_abbr}"

    $"""Convert the following text into segments. Each segment is either plain text or a link.

Text to convert:
{text}

Rules for creating segments:
1. For player names, create a link segment with URL to their Wikipedia page (https://en.wikipedia.org/wiki/Player_Name with underscores)
2. For team names (full names like "Boston Celtics" or city names like "Boston" when referring to a team), create a link segment with URL pattern: {leagueTeamUrlPattern}
   - Use lowercase team abbreviations (e.g., bos, lal, nyy, dal)
3. All other text should be plain text segments
4. Preserve the exact wording and punctuation
5. Keep segments logically grouped - don't split mid-sentence unnecessarily

Return JSON in this exact format:
{{"segments": [
  {{"type": "text", "value": "The "}},
  {{"type": "link", "value": "Boston Celtics", "url": "https://www.espn.com/nba/team/_/name/bos"}},
  {{"type": "text", "value": " defeated the "}},
  {{"type": "link", "value": "Los Angeles Lakers", "url": "https://www.espn.com/nba/team/_/name/lal"}},
  {{"type": "text", "value": ". "}},
  {{"type": "link", "value": "LeBron James", "url": "https://en.wikipedia.org/wiki/LeBron_James"}},
  {{"type": "text", "value": " scored 30 points."}}
]}}

Return ONLY the JSON, no other text."""

let private parseSegmentsResponse (text: string) : TextSegment list =
    try
        let startIdx = text.IndexOf('{')
        let endIdx = text.LastIndexOf('}')
        if startIdx >= 0 && endIdx > startIdx then
            let jsonPart = text.Substring(startIdx, endIdx - startIdx + 1)
            printfn "      [TextSegmentConverter] Parsing JSON: %s" (jsonPart.Substring(0, min 200 jsonPart.Length))
            let options = JsonSerializerOptions()
            options.AllowTrailingCommas <- true
            let response = JsonSerializer.Deserialize<TextSegmentsResponse>(jsonPart, options)
            response.Segments |> Array.toList
        else
            // Return original text as single segment
            [{ Type = "text"; Value = text; Url = null }]
    with ex ->
        printfn "      [TextSegmentConverter] JSON parse error: %s" ex.Message
        // Return original text as single segment on error
        [{ Type = "text"; Value = text; Url = null }]

/// Convert a text block into segments with links
let convertToSegments (client: HttpClient) (apiKey: string) (league: string) (text: string) = async {
    if String.IsNullOrWhiteSpace(text) then
        return []
    else
        let prompt = buildSegmentPrompt text league
        let! response = CallGemini.callGemini client apiKey prompt 0.2
        match response with
        | Some responseText ->
            let segments = parseSegmentsResponse responseText
            return segments
        | None ->
            // Return original text as single segment on error
            return [{ Type = "text"; Value = text; Url = null }]
}

// Updated Narrative type with segments
type NarrativeWithSegments = {
    [<JsonPropertyName("title")>]
    Title: string
    [<JsonPropertyName("summary")>]
    Summary: string
    [<JsonPropertyName("summarySegments")>]
    SummarySegments: TextSegment list
    [<JsonPropertyName("league")>]
    League: string
    [<JsonPropertyName("links")>]
    Links: RelevantLink list
    [<JsonPropertyName("dataPoints")>]
    DataPoints: DataPoint list
    [<JsonPropertyName("statisticalContext")>]
    StatisticalContext: string
    [<JsonPropertyName("statisticalContextSegments")>]
    StatisticalContextSegments: TextSegment list
}

/// Convert narratives to include text segments
let enrichWithSegments (narratives: Narrative list) = async {
    if List.isEmpty narratives then
        return []
    else
        printfn "  Converting text to segments..."
        let apiKey = CallGemini.getApiKey()
        use client = new HttpClient()
        client.Timeout <- TimeSpan.FromMinutes(2.0)

        let! enriched =
            narratives
            |> List.mapi (fun i narrative -> async {
                printfn "    [%d/%d] Processing %s narrative..." (i + 1) (List.length narratives) narrative.League

                // Convert summary to segments
                let! summarySegments = convertToSegments client apiKey narrative.League narrative.Summary
                printfn "      Summary: %d segments" (List.length summarySegments)

                // Convert statistical context to segments
                let! contextSegments = convertToSegments client apiKey narrative.League narrative.StatisticalContext
                printfn "      Context: %d segments" (List.length contextSegments)

                return {
                    Title = narrative.Title
                    Summary = narrative.Summary
                    SummarySegments = summarySegments
                    League = narrative.League
                    Links = narrative.Links
                    DataPoints = narrative.DataPoints
                    StatisticalContext = narrative.StatisticalContext
                    StatisticalContextSegments = contextSegments
                }
            })
            |> Async.Sequential

        printfn "  Done converting segments!"
        return enriched |> Array.toList
}
