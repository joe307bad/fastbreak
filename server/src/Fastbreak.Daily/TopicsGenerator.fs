module TopicsGenerator

open System
open System.Net.Http
open System.Text
open System.Text.Json
open System.Text.Json.Serialization

type Month =
    | January = 1
    | February = 2
    | March = 3
    | April = 4
    | May = 5
    | June = 6
    | July = 7
    | August = 8
    | September = 9
    | October = 10
    | November = 11
    | December = 12

type SeasonPhase =
    | RegularSeason
    | Playoffs
    | Offseason

type SeasonPeriod = {
    StartMonth: Month
    EndMonth: Month
}

type SeasonInfo = {
    League: string
    RegularSeason: SeasonPeriod
    Postseason: SeasonPeriod
    Offseason: SeasonPeriod
}

let sportsSeasons = [
    { League = "NFL"
      RegularSeason = { StartMonth = Month.September; EndMonth = Month.January }
      Postseason = { StartMonth = Month.January; EndMonth = Month.February }
      Offseason = { StartMonth = Month.March; EndMonth = Month.August } }

    { League = "NBA"
      RegularSeason = { StartMonth = Month.October; EndMonth = Month.April }
      Postseason = { StartMonth = Month.April; EndMonth = Month.June }
      Offseason = { StartMonth = Month.July; EndMonth = Month.September } }

    { League = "NHL"
      RegularSeason = { StartMonth = Month.October; EndMonth = Month.April }
      Postseason = { StartMonth = Month.April; EndMonth = Month.June }
      Offseason = { StartMonth = Month.July; EndMonth = Month.September } }

    { League = "MLB"
      RegularSeason = { StartMonth = Month.April; EndMonth = Month.September }
      Postseason = { StartMonth = Month.October; EndMonth = Month.November }
      Offseason = { StartMonth = Month.November; EndMonth = Month.March } }
]

let seasonsByLeague =
    sportsSeasons
    |> List.map (fun s -> s.League, s)
    |> Map.ofList

let isInPeriod (period: SeasonPeriod) (month: Month) : bool =
    let m = int month
    let startMonth = int period.StartMonth
    let endMonth = int period.EndMonth

    if startMonth <= endMonth then
        m >= startMonth && m <= endMonth
    else
        m >= startMonth || m <= endMonth

let getCurrentPhase (season: SeasonInfo) (month: Month) : SeasonPhase =
    if isInPeriod season.Postseason month then Playoffs
    elif isInPeriod season.RegularSeason month then RegularSeason
    else Offseason

// Gemini API types
type GeminiPart = { text: string }
type GeminiContent = { parts: GeminiPart list }
type GoogleSearch = { google_search: obj }
type GeminiGenerationConfig = { temperature: float; maxOutputTokens: int }
type GeminiRequest = {
    contents: GeminiContent list
    tools: GoogleSearch list
    generationConfig: GeminiGenerationConfig
}

// Grounding metadata types for extracting URLs
type WebChunk = { uri: string; title: string }
type GroundingChunk = { web: WebChunk option }
type GroundingMetadata = {
    [<JsonPropertyName("groundingChunks")>]
    groundingChunks: GroundingChunk list option
}
type GeminiCandidate = {
    content: GeminiContent
    [<JsonPropertyName("groundingMetadata")>]
    groundingMetadata: GroundingMetadata option
}
type GeminiResponse = { candidates: GeminiCandidate list }

// Output types
type RelevantLink = { title: string; url: string }
type Narrative = {
    title: string
    summary: string
    league: string
    tier: int
    links: RelevantLink list
}
type TopicsOutput = { narratives: Narrative list }

// Intermediate type for parsing (without links)
type ParsedNarrative = {
    title: string
    summary: string
    league: string
    tier: int
}

let phaseToString = function
    | Playoffs -> "Postseason"
    | RegularSeason -> "Regular Season"
    | Offseason -> "Offseason"

let getLeaguesByTier () =
    let currentMonth = DateTime.UtcNow.Month |> enum<Month>

    let leaguesWithPhase =
        sportsSeasons
        |> List.map (fun season ->
            let phase = getCurrentPhase season currentMonth
            season.League, phase)

    let tier1 = leaguesWithPhase |> List.filter (fun (_, p) -> p = Playoffs) |> List.map fst
    let tier2 = leaguesWithPhase |> List.filter (fun (_, p) -> p = RegularSeason) |> List.map fst
    let tier3 = leaguesWithPhase |> List.filter (fun (_, p) -> p = Offseason) |> List.map fst

    tier1, tier2, tier3

let buildPrompt () =
    let tier1, tier2, tier3 = getLeaguesByTier ()
    let today = DateTime.UtcNow.ToString("yyyy-MM-dd")

    let formatLeagues leagues =
        if List.isEmpty leagues then "None"
        else leagues |> String.concat ", "

    sprintf """Today's date is %s.

Current status of the four major North American sports leagues:

TIER 1 - POSTSEASON (Highest Priority): %s
TIER 2 - REGULAR SEASON (Medium Priority): %s
TIER 3 - OFFSEASON (Lowest Priority): %s

Generate 5 interesting sports narratives using web search for current storylines.

TIER RULES:
- Tier 1 leagues (Postseason) get TOP priority - first narratives MUST be about these leagues
- Tier 2 leagues (Regular Season) get NEXT priority
- Tier 3 leagues (Offseason) get LOWEST priority - only if slots remain

NARRATIVE REQUIREMENTS:
- RECENCY: Must be about events from the last 7 days. Search for what happened THIS WEEK.
- NO REPETITION: Each narrative must cover a different team, player, or storyline. No overlap.
- STATS REQUIRED: Every summary MUST include at least 1-2 specific statistics (points, yards, percentages, records, etc.)
- AUTHENTIC VOICE: Write like a sports journalist, not an AI. Be specific, opinionated, and direct. Avoid generic phrases like "continues to impress" or "showing their dominance".
- NO REDUNDANCY: Don't state the obvious. If a team won, tell us WHY it matters or what was surprising.
- UNIQUE ANGLES: Find the interesting story within the story. What's the narrative that casual fans might miss?
- GROUNDED: Stay focused on real, recent events. Don't speculate or make predictions.

BAD EXAMPLE (generic, no stats, AI-sounding):
"The Chiefs continue their impressive run as they prepare for another championship appearance."

GOOD EXAMPLE (specific, stats, authentic):
"Patrick Mahomes threw for 245 yards and 2 TDs in the AFC Championship, but it was his 3rd-down scramble with 2:47 left that sealed Kansas City's fifth straight conference title."

IMPORTANT: Return ONLY valid JSON, no other text. No preamble, no explanation.

{
  "narratives": [
    {
      "title": "string",
      "summary": "string (2-3 sentences with specific stats)",
      "league": "NFL|NBA|NHL|MLB",
      "tier": 1|2|3
    }
  ]
}""" today (formatLeagues tier1) (formatLeagues tier2) (formatLeagues tier3)

// Find articles for a specific narrative (single attempt)
let tryFindArticles (httpClient: HttpClient) (apiKey: string) (narrative: ParsedNarrative) (attempt: int) = async {
    // Use different search strategies on retries
    let searchQuery =
        match attempt with
        | 1 -> narrative.title + " " + narrative.league + " news articles"
        | 2 -> narrative.summary.Substring(0, min 100 narrative.summary.Length) + " " + narrative.league
        | _ -> narrative.league + " " + narrative.title + " analysis opinion"

    let prompt =
        "Search for and cite 3 recent sports articles about: " + searchQuery + "\n\n" +
        "Context: " + narrative.summary + "\n\n" +
        "REQUIREMENTS:\n" +
        "- Find REAL articles from sports news sites (ESPN, The Athletic, CBS Sports, SI, etc.)\n" +
        "- Each article should cover a DIFFERENT angle (news, analysis, opinion)\n" +
        "- Articles must be from the last 7 days\n" +
        "- Provide the EXACT article titles as they appear on the source\n\n" +
        "Return ONLY valid JSON:\n" +
        """{"articles": [{"title": "Exact Article Title 1"}, {"title": "Exact Article Title 2"}, {"title": "Exact Article Title 3"}]}"""

    let request = {
        contents = [ { parts = [ { text = prompt } ] } ]
        tools = [ { google_search = obj() } ]
        generationConfig = { temperature = 0.3 + (float attempt * 0.2); maxOutputTokens = 1000 }
    }

    let requestJson = JsonSerializer.Serialize request
    let apiUrl = sprintf "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=%s" apiKey
    let content = new StringContent(requestJson, Encoding.UTF8, "application/json")

    let! response = httpClient.PostAsync(apiUrl, content) |> Async.AwaitTask
    let! responseBody = response.Content.ReadAsStringAsync() |> Async.AwaitTask

    if not response.IsSuccessStatusCode then
        printfn "    Article search failed: %d" (int response.StatusCode)
        return ([], [])
    else
        let options = JsonSerializerOptions()
        options.PropertyNameCaseInsensitive <- true
        let geminiResponse = JsonSerializer.Deserialize<GeminiResponse>(responseBody, options)

        let candidate = geminiResponse.candidates |> List.tryHead

        // Extract titles from Gemini's response
        let responseText =
            candidate
            |> Option.bind (fun c -> c.content.parts |> List.tryHead)
            |> Option.map (fun p -> p.text)
            |> Option.defaultValue ""

        let cleanedResponse =
            responseText
                .Replace("```json", "")
                .Replace("```", "")
                .Trim()

        let titles =
            try
                let articlesJson = JsonDocument.Parse cleanedResponse
                let articlesArray = articlesJson.RootElement.GetProperty("articles")
                [ for i in 0 .. articlesArray.GetArrayLength() - 1 do
                    let a = articlesArray.[i]
                    yield a.GetProperty("title").GetString() ]
            with ex ->
                printfn "    Failed to parse article titles: %s" ex.Message
                []

        // Extract URLs from grounding metadata
        let groundingUrls =
            candidate
            |> Option.bind (fun c -> c.groundingMetadata)
            |> Option.bind (fun gm -> gm.groundingChunks)
            |> Option.defaultValue []
            |> List.choose (fun chunk -> chunk.web |> Option.map (fun web -> web.uri))

        printfn "    Attempt %d: Found %d titles, %d URLs" attempt titles.Length groundingUrls.Length

        // Pair titles with URLs - only pair up to the minimum of both lists
        let minCount = min (min titles.Length groundingUrls.Length) 3
        let links =
            if minCount = 0 then
                []
            else
                [ for i in 0 .. minCount - 1 do
                    yield { title = titles.[i]; url = groundingUrls.[i] } ]

        // Return both links and titles (for fallback if URLs never come)
        return (links, titles)
}

// Find articles with retry logic to ensure we get 3 links
let findArticlesForNarrative (httpClient: HttpClient) (apiKey: string) (narrative: ParsedNarrative) = async {
    printfn "  Finding articles for: %s" narrative.title

    let rec tryWithRetry attempt lastTitles =
        async {
            if attempt > 3 then
                // Fallback: if we have titles but no URLs, return titles with empty URLs
                if List.length lastTitles >= 3 then
                    printfn "    Using titles without URLs as fallback"
                    return lastTitles |> List.truncate 3 |> List.map (fun t -> { title = t; url = "" })
                else
                    printfn "    Failed to get 3 links after 3 attempts"
                    return []
            else
                let! (links, titles) = tryFindArticles httpClient apiKey narrative attempt
                if List.length links >= 3 then
                    return links
                else
                    printfn "    Only got %d links, retrying..." (List.length links)
                    // Keep the best titles we've found
                    let bestTitles = if List.length titles > List.length lastTitles then titles else lastTitles
                    return! tryWithRetry (attempt + 1) bestTitles
        }

    return! tryWithRetry 1 []
}

let generateTopics () = async {
    let apiKey = Environment.GetEnvironmentVariable "GEMINI_API_KEY"

    if String.IsNullOrEmpty apiKey then
        failwith "GEMINI_API_KEY environment variable not set"

    let prompt = buildPrompt ()

    let request = {
        contents = [ { parts = [ { text = prompt } ] } ]
        tools = [ { google_search = obj() } ]
        generationConfig = { temperature = 0.7; maxOutputTokens = 4000 }
    }

    let requestJson = JsonSerializer.Serialize request

    use httpClient = new HttpClient()
    httpClient.Timeout <- TimeSpan.FromMinutes 2.0

    let apiUrl = sprintf "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=%s" apiKey
    let content = new StringContent(requestJson, Encoding.UTF8, "application/json")

    printfn "Generating narratives..."
    let! response = httpClient.PostAsync(apiUrl, content) |> Async.AwaitTask
    let! responseBody = response.Content.ReadAsStringAsync() |> Async.AwaitTask

    if not response.IsSuccessStatusCode then
        failwith (sprintf "Gemini API request failed with status %d: %s" (int response.StatusCode) responseBody)

    let options = JsonSerializerOptions()
    options.PropertyNameCaseInsensitive <- true
    let geminiResponse = JsonSerializer.Deserialize<GeminiResponse>(responseBody, options)

    let responseText =
        geminiResponse.candidates
        |> List.tryHead
        |> Option.bind (fun c -> c.content.parts |> List.tryHead)
        |> Option.map (fun p -> p.text)
        |> Option.defaultValue ""

    let cleanedResponse =
        responseText
            .Replace("```json", "")
            .Replace("```", "")
            .Trim()

    try
        let narrativesJson = JsonDocument.Parse cleanedResponse
        let narrativesArray = narrativesJson.RootElement.GetProperty("narratives")

        let parsedNarratives =
            [ for i in 0 .. narrativesArray.GetArrayLength() - 1 do
                let n = narrativesArray.[i]
                {
                    title = n.GetProperty("title").GetString()
                    summary = n.GetProperty("summary").GetString()
                    league = n.GetProperty("league").GetString()
                    tier = n.GetProperty("tier").GetInt32()
                }
            ]

        printfn "Generated %d narratives" parsedNarratives.Length
        printfn ""
        printfn "Finding relevant articles for each narrative..."

        // Find articles for each narrative
        let! narrativesWithLinks =
            parsedNarratives
            |> List.map (fun n -> async {
                let! links = findArticlesForNarrative httpClient apiKey n
                return {
                    title = n.title
                    summary = n.summary
                    league = n.league
                    tier = n.tier
                    links = links
                }
            })
            |> Async.Sequential

        let output = { narratives = narrativesWithLinks |> Array.toList }
        let outputOptions = JsonSerializerOptions()
        outputOptions.WriteIndented <- true
        return JsonSerializer.Serialize(output, outputOptions)
    with ex ->
        printfn "Failed to parse narratives: %s" ex.Message
        printfn "Raw response: %s" cleanedResponse
        return cleanedResponse
}
