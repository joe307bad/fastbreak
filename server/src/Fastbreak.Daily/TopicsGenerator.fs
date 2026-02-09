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

// Request without tools (for non-grounded calls)
type GeminiRequestNoTools = {
    contents: GeminiContent list
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
type RelevantLink = {
    title: string
    url: string
    [<JsonPropertyName("type")>]
    linkType: string
}
type DataPoint = { metric: string; value: string; context: string }
type Narrative = {
    title: string
    summary: string
    league: string
    tier: int
    links: RelevantLink list
    dataPoints: DataPoint list
}
type TopicsOutput = { date: string; narratives: Narrative list }

// Intermediate type for parsing (without links/dataPoints)
type ParsedNarrative = {
    title: string
    summary: string
    league: string
    tier: int
}

// Chart registry types
type ChartRegistryItem = { key: string }

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

// Download chart registry and all chart data
let downloadChartData (httpClient: HttpClient) = async {
    let baseUrl = "https://d2jyizt5xogu23.cloudfront.net/"
    let registryUrl = baseUrl + "registry"

    printfn "Downloading chart registry..."
    let! registryResponse = httpClient.GetStringAsync(registryUrl) |> Async.AwaitTask

    // Parse registry to get all chart keys
    let registryDoc = JsonDocument.Parse(registryResponse)
    let chartKeys =
        [ for prop in registryDoc.RootElement.EnumerateObject() do
            // Skip topics.json, we only want chart data
            if not (prop.Name.Contains("topics")) then
                yield prop.Name ]

    printfn "Found %d charts in registry" chartKeys.Length

    // Download each chart
    let! charts =
        chartKeys
        |> List.map (fun key -> async {
            try
                let chartUrl = baseUrl + key
                let! chartJson = httpClient.GetStringAsync(chartUrl) |> Async.AwaitTask
                printfn "  Downloaded: %s" key
                return Some (key, chartJson)
            with ex ->
                printfn "  Failed to download %s: %s" key ex.Message
                return None
        })
        |> Async.Sequential

    let chartData =
        charts
        |> Array.choose id
        |> Array.toList

    printfn "Downloaded %d charts successfully" chartData.Length
    return chartData
}

// Find data points for a narrative using chart data
let findDataPointsForNarrative (httpClient: HttpClient) (apiKey: string) (chartData: (string * string) list) (narrative: ParsedNarrative) = async {
    printfn "  Finding data points for: %s" narrative.title

    // Filter charts relevant to this league
    let leagueLower = narrative.league.ToLower()
    let relevantCharts =
        chartData
        |> List.filter (fun (key, _) -> key.ToLower().Contains(leagueLower))

    if List.isEmpty relevantCharts then
        printfn "    No charts found for league: %s" narrative.league
        return []
    else
        // Build a condensed version of chart data for the prompt
        let chartSummary =
            relevantCharts
            |> List.map (fun (key, json) ->
                let chartName = key.Replace("dev/", "").Replace(".json", "")
                sprintf "--- Chart: %s ---\n%s" chartName (json.Substring(0, min 3000 json.Length)))
            |> String.concat "\n\n"

        let prompt =
            "Analyze this sports narrative and the available chart data to find 3 relevant data points.\n\n" +
            "NARRATIVE:\n" +
            "Title: " + narrative.title + "\n" +
            "Summary: " + narrative.summary + "\n" +
            "League: " + narrative.league + "\n\n" +
            "IMPORTANT - TEAM ABBREVIATIONS:\n" +
            "Teams in the chart data use 3-letter abbreviations. Examples:\n" +
            "- Seattle Seahawks = SEA, Kansas City Chiefs = KC, Philadelphia Eagles = PHI\n" +
            "- Los Angeles Lakers = LAL, Boston Celtics = BOS, Golden State Warriors = GSW\n" +
            "- New York Yankees = NYY, Los Angeles Dodgers = LAD, Atlanta Braves = ATL\n" +
            "- Colorado Avalanche = COL, Edmonton Oilers = EDM, Florida Panthers = FLA\n" +
            "You MUST look up the team by its abbreviation, not its full name.\n\n" +
            "AVAILABLE CHART DATA:\n" + chartSummary + "\n\n" +
            "TASK:\n" +
            "1. Identify the team(s) or player(s) mentioned in the narrative, then find their ABBREVIATION in the data\n" +
            "2. Find 3 DIFFERENT statistics from the chart data - DO NOT repeat similar metrics\n" +
            "3. For each stat, provide metric name, value, and context\n\n" +
            "CONTEXT REQUIREMENTS:\n" +
            "- 2-3 sentences: First sentence explains the stat with league ranking. Second sentence connects it to the narrative.\n" +
            "- ALWAYS include league-wide ranking (e.g., 'top 5 in the league', 'bottom third', 'middle of the pack', 'leads the league')\n" +
            "- Be direct and specific - no filler words or verbose explanations\n" +
            "- Each data point must offer a UNIQUE insight - no redundancy\n\n" +
            "BAD: 'The team has an offensive rating of 117.7 which is a very good rating and shows they are efficient.'\n" +
            "GOOD: 'Ranks 3rd in offensive efficiency. Their spacing and ball movement create open looks, which showed in their playoff dominance.'\n\n" +
            "Return ONLY valid JSON (value must be a STRING):\n" +
            """{"dataPoints": [{"metric": "Stat Name", "value": "123.4", "context": "League ranking context. How it connects to the narrative."}]}"""

        let request = {
            contents = [ { parts = [ { text = prompt } ] } ]
            generationConfig = { temperature = 0.3; maxOutputTokens = 1500 }
        }

        let requestJson = JsonSerializer.Serialize request
        let apiUrl = sprintf "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=%s" apiKey
        let content = new StringContent(requestJson, Encoding.UTF8, "application/json")

        let! response = httpClient.PostAsync(apiUrl, content) |> Async.AwaitTask
        let! responseBody = response.Content.ReadAsStringAsync() |> Async.AwaitTask

        if not response.IsSuccessStatusCode then
            printfn "    Data point search failed: %d" (int response.StatusCode)
            return []
        else
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
                let dataPointsJson = JsonDocument.Parse cleanedResponse
                let dataPointsArray = dataPointsJson.RootElement.GetProperty("dataPoints")
                let dataPoints =
                    [ for i in 0 .. min 2 (dataPointsArray.GetArrayLength() - 1) do
                        let dp = dataPointsArray.[i]
                        // Handle value as either string or number
                        let valueProp = dp.GetProperty("value")
                        let valueStr =
                            match valueProp.ValueKind with
                            | JsonValueKind.String -> valueProp.GetString()
                            | JsonValueKind.Number -> valueProp.GetRawText()
                            | _ -> valueProp.GetRawText()
                        yield {
                            metric = dp.GetProperty("metric").GetString()
                            value = valueStr
                            context = dp.GetProperty("context").GetString()
                        } ]
                printfn "    Found %d data points" dataPoints.Length
                for dp in dataPoints do
                    printfn "      - %s: %s" dp.metric dp.value
                return dataPoints
            with ex ->
                printfn "    Failed to parse data points: %s" ex.Message
                return []
}

let buildPrompt () =
    let tier1, tier2, tier3 = getLeaguesByTier ()
    let now = DateTime.UtcNow
    let today = now.ToString("yyyy-MM-dd")
    let threeDaysAgo = now.AddDays(-3.0).ToString("yyyy-MM-dd")

    let formatLeagues leagues =
        if List.isEmpty leagues then "None"
        else leagues |> String.concat ", "

    sprintf """⚠️ CRITICAL DATE CONSTRAINT ⚠️
TODAY IS: %s
ONLY cover events from %s to %s (the last 72 hours).
ANY event before %s is TOO OLD - DO NOT USE IT.

Current status of the four major North American sports leagues:

TIER 1 - POSTSEASON (Highest Priority): %s
TIER 2 - REGULAR SEASON (Medium Priority): %s
TIER 3 - OFFSEASON (Lowest Priority): %s

Generate 5 interesting sports narratives using web search for current storylines.

TIER RULES:
- Tier 1 leagues (Postseason) get TOP priority - first narratives MUST be about these leagues
- Tier 2 leagues (Regular Season) get NEXT priority
- Tier 3 leagues (Offseason) get LOWEST priority - only if slots remain

⚠️ ABSOLUTE RECENCY REQUIREMENT - READ CAREFULLY ⚠️
You MUST verify that each event happened between %s and %s.
- Search query MUST include the current date or "today" or "yesterday"
- If an article is from December, January, or early February - REJECT IT
- If a game happened more than 3 days ago - REJECT IT
- When you find search results, CHECK THE DATE before using them
- Example: If today is Feb 9, a game from Feb 6 or earlier is TOO OLD

NARRATIVE REQUIREMENTS:
- VERIFIED RECENT: Every narrative must be about an event from the last 72 hours. Verify the date.
- NO REPETITION: Each narrative must cover a different team, player, or storyline. No overlap.
- STATS REQUIRED: Every summary MUST include at least 1-2 specific statistics (points, yards, percentages, records, etc.)
- AUTHENTIC VOICE: Write like a sports journalist, not an AI. Be specific, opinionated, and direct.
- UNIQUE ANGLES: Find the interesting story within the story.

BAD EXAMPLE (old news - REJECT):
"The Oilers defeated the Golden Knights 4-3..." (if this game was from December)

GOOD EXAMPLE (verified recent):
"Last night's thriller saw the Oilers edge Vegas 3-2 in overtime, with McDavid's 35th goal of the season..."

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
}""" today threeDaysAgo today threeDaysAgo (formatLeagues tier1) (formatLeagues tier2) (formatLeagues tier3) threeDaysAgo today

// Find articles for a specific narrative (single attempt)
let tryFindArticles (httpClient: HttpClient) (apiKey: string) (narrative: ParsedNarrative) (attempt: int) = async {
    // Use different search strategies on retries
    let searchQuery =
        match attempt with
        | 1 -> narrative.title + " " + narrative.league + " news articles"
        | 2 -> narrative.summary.Substring(0, min 100 narrative.summary.Length) + " " + narrative.league
        | _ -> narrative.league + " " + narrative.title + " analysis opinion"

    let prompt =
        "Search for and cite 3 recent sports articles related to: " + searchQuery + "\n\n" +
        "Context: " + narrative.summary + "\n\n" +
        "CRITICAL - EACH ARTICLE MUST BE ABOUT A DIFFERENT TOPIC:\n" +
        "Only 1 article can be about the specific event in the narrative. The other 2 MUST be about DIFFERENT topics.\n\n" +
        "REQUIRED MIX (choose the pattern that fits):\n" +
        "- For a TRADE: 1 trade article, 1 about the team's season/outlook, 1 about another player on the team\n" +
        "- For a GAME: 1 recap, 1 about a key player's season stats, 1 about playoff race/standings\n" +
        "- For PLAYER NEWS (investment, injury, award): 1 about the news, 1 about their on-court performance, 1 about their team\n" +
        "- For any story: Article 2 and 3 should be about the TEAM or PLAYER more broadly, NOT the same headline\n\n" +
        "BAD EXAMPLE (all same story):\n" +
        "1. 'SGA Invests in Hamilton Arena' 2. 'SGA Becomes Part-Owner of Arena' 3. 'Thunder Star Buys Into Coliseum'\n\n" +
        "GOOD EXAMPLE (diverse topics):\n" +
        "1. 'SGA Invests in Hamilton Arena' 2. 'Thunder Lead West with League-Best Record' 3. 'SGA MVP Case: Breaking Down His Historic Numbers'\n\n" +
        "REQUIREMENTS:\n" +
        "- Find REAL articles from sports news sites (ESPN, The Athletic, CBS Sports, SI, etc.)\n" +
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
                    yield { title = titles.[i]; url = groundingUrls.[i]; linkType = "news" } ]

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
                    return lastTitles |> List.truncate 3 |> List.map (fun t -> { title = t; url = ""; linkType = "news" })
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

    use httpClient = new HttpClient()
    httpClient.Timeout <- TimeSpan.FromMinutes 2.0

    // Step 1: Download all chart data
    let! chartData = downloadChartData httpClient
    printfn ""

    // Step 2: Generate narratives
    let prompt = buildPrompt ()

    let request = {
        contents = [ { parts = [ { text = prompt } ] } ]
        tools = [ { google_search = obj() } ]
        generationConfig = { temperature = 0.7; maxOutputTokens = 4000 }
    }

    let requestJson = JsonSerializer.Serialize request

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
        printfn "Enriching narratives with links and data points..."

        // Step 3: Find articles and data points for each narrative
        let! narrativesWithData =
            parsedNarratives
            |> List.map (fun n -> async {
                let! links = findArticlesForNarrative httpClient apiKey n
                let! dataPoints = findDataPointsForNarrative httpClient apiKey chartData n
                return {
                    title = n.title
                    summary = n.summary
                    league = n.league
                    tier = n.tier
                    links = links
                    dataPoints = dataPoints
                }
            })
            |> Async.Sequential

        let output = {
            date = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
            narratives = narrativesWithData |> Array.toList
        }
        let outputOptions = JsonSerializerOptions()
        outputOptions.WriteIndented <- true
        return JsonSerializer.Serialize(output, outputOptions)
    with ex ->
        printfn "Failed to parse narratives: %s" ex.Message
        printfn "Raw response: %s" cleanedResponse
        return cleanedResponse
}
