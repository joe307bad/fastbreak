open System
open System.IO
open System.Net.Http
open System.Text
open System.Text.Json
open System.Text.Json.Serialization
open Amazon
open Amazon.S3
open Amazon.S3.Model

// Types for structured JSON output
type DataPoint = {
    [<JsonPropertyName("metric")>]
    Metric: string
    [<JsonPropertyName("value")>]
    Value: string
    [<JsonPropertyName("context")>]
    Context: string
}

type ChartReference = {
    [<JsonPropertyName("chartName")>]
    ChartName: string
    [<JsonPropertyName("chartUrl")>]
    ChartUrl: string
    [<JsonPropertyName("relevance")>]
    Relevance: string
}

type ChartHighlight = {
    [<JsonPropertyName("chartName")>]
    ChartName: string
    [<JsonPropertyName("highlightType")>]
    HighlightType: string  // "player", "team", "conference", "division"
    [<JsonPropertyName("values")>]
    Values: string list  // e.g., ["BOS", "MIL"] for teams, ["Connor McDavid", "Nathan MacKinnon"] for players
}

type LinkReference = {
    [<JsonPropertyName("title")>]
    Title: string
    [<JsonPropertyName("url")>]
    Url: string
    [<JsonPropertyName("type")>]
    Type: string  // "news" or "blog"
}

type Narrative = {
    [<JsonPropertyName("title")>]
    Title: string
    [<JsonPropertyName("summary")>]
    Summary: string
    [<JsonPropertyName("chartEvidence")>]
    ChartEvidence: ChartReference list
    [<JsonPropertyName("highlights")>]
    Highlights: ChartHighlight list
    [<JsonPropertyName("dataPoints")>]
    DataPoints: DataPoint list
    [<JsonPropertyName("links")>]
    Links: LinkReference list
}

type SportsNarrativesResponse = {
    [<JsonPropertyName("date")>]
    Date: string
    [<JsonPropertyName("narratives")>]
    Narratives: Narrative list
}

// Gemini API request/response types
type GeminiPart = {
    text: string
}

type GeminiContent = {
    parts: GeminiPart list
}

type GoogleSearch = {
    google_search: obj  // Empty object to enable grounding
}

type GeminiGenerationConfig = {
    temperature: float
    maxOutputTokens: int
}

type GeminiRequest = {
    contents: GeminiContent list
    tools: GoogleSearch list
    generationConfig: GeminiGenerationConfig
}

type GeminiCandidate = {
    content: GeminiContent
}

type GeminiResponse = {
    candidates: GeminiCandidate list
}

// Allowlist of reputable sports analytics sources
let allowedDomains = [
    // Major sports news sites
    "espn.com"; "theathletic.com"; "si.com"; "bleacherreport.com"; "yahoo.com/sports"
    // Advanced analytics sites
    "fivethirtyeight.com"; "theringer.com"; "statmuse.com"; "hockey-reference.com"
    "basketball-reference.com"; "baseball-reference.com"; "pro-football-reference.com"
    "pff.com"; "footballoutsiders.com"; "chartball.com"
    // Independent writers and platforms
    "substack.com"; "medium.com"
    // League official sites
    "nba.com"; "nhl.com"; "nfl.com"; "mlb.com"; "mls.com"
    // Analytics podcasts/networks
    "lockedon.com"; "duncdOn.supportingcast.fm"
    // Team analytics blogs
    "sbnation.com"
]

let allowedDomainsString =
    allowedDomains
    |> List.map (sprintf "  - %s")
    |> String.concat "\n"

let fetchChartData () = async {
    use httpClient = new HttpClient()

    // Fetch registry
    let! registryResponse = httpClient.GetStringAsync("https://d2jyizt5xogu23.cloudfront.net/registry") |> Async.AwaitTask
    let registry = JsonSerializer.Deserialize<System.Collections.Generic.Dictionary<string, JsonElement>>(registryResponse)

    printfn "Found %d charts in registry" registry.Count

    // Download all chart JSONs
    let! chartDataTasks =
        registry.Keys
        |> Seq.map (fun chartFile ->
            async {
                let url = sprintf "https://d2jyizt5xogu23.cloudfront.net/%s" chartFile
                try
                    let! data = httpClient.GetStringAsync(url) |> Async.AwaitTask
                    return Some (chartFile, data)
                with ex ->
                    eprintfn "Failed to fetch %s: %s" chartFile ex.Message
                    return None
            })
        |> Async.Parallel

    let chartData =
        chartDataTasks
        |> Array.choose id
        |> Array.toList

    printfn "Successfully downloaded %d charts" chartData.Length
    return chartData
}

let uploadToS3 (bucketName: string) (json: string) = async {
    let region =
        match Environment.GetEnvironmentVariable "AWS_REGION" with
        | null | "" ->
            match Environment.GetEnvironmentVariable "AWS_DEFAULT_REGION" with
            | null | "" -> RegionEndpoint.USEast1
            | r -> RegionEndpoint.GetBySystemName(r)
        | r -> RegionEndpoint.GetBySystemName(r)
    use s3Client = new AmazonS3Client(region)

    let request = PutObjectRequest()
    request.BucketName <- bucketName
    request.Key <- "dev/topics.json"
    request.ContentType <- "application/json"
    request.ContentBody <- json

    let! response = s3Client.PutObjectAsync(request) |> Async.AwaitTask
    printfn "Uploaded to s3://%s/dev/topics.json (HTTP %d)" bucketName (int response.HttpStatusCode)
}

let getSportsNarratives () = async {
    // Get API key from environment
    let apiKey = Environment.GetEnvironmentVariable "GEMINI_API_KEY"

    if String.IsNullOrEmpty apiKey then
        failwith "GEMINI_API_KEY environment variable not set"

    // Fetch all chart data
    printfn "Fetching chart data from CloudFront..."
    let! charts = fetchChartData()

    // Build context from charts with increased limit to capture actual data points
    let chartsContext =
        charts
        |> List.map (fun (name, data) ->
            sprintf "Chart: %s\nData: %s\n" name (data.Substring(0, min 3000 data.Length)))
        |> String.concat "\n---\n"

    // Prepare the prompt for Gemini
    let today = DateTime.UtcNow.ToString("yyyy-MM-dd")
    let timestamp = DateTime.UtcNow.ToString("o")
    let prompt =
        sprintf """Today's date is %s.

I have the following sports analytics charts available:

%s

Based on these charts, search the web for current sports news, blog posts about advanced analytics, and create 5 compelling narratives that use the chart data as evidence.

SEASONAL CONTEXT AND RELEVANCE REQUIREMENTS:
- CRITICAL: Consider the current date (%s) and the season phase for each sport (regular season, playoffs, offseason)

- For NBA:
  * Regular season: Oct-Apr
  * Play-in tournament: Mid-April (only 16 teams remain)
  * First round: Late Apr-Early May (only 16 teams remain)
  * Conference Semifinals: Early-Mid May (only 8 teams remain)
  * Conference Finals: Mid-Late May (only 4 teams remain - focus ONLY on these 4)
  * NBA Finals: Early June (only 2 teams remain - focus ONLY on these 2)
  * After NBA Finals: Offseason - focus on draft, free agency, summer league
  * CRITICAL: Once playoffs start, most teams are ELIMINATED - do NOT feature eliminated teams

- For NHL:
  * Regular season: Oct-Apr
  * First round: Mid-Late Apr (only 16 teams remain)
  * Second round: Early-Mid May (only 8 teams remain)
  * Conference Finals: Mid-Late May (only 4 teams remain - focus ONLY on these 4)
  * Stanley Cup Finals: Early June (only 2 teams remain - focus ONLY on these 2)
  * After Stanley Cup Finals: Offseason - focus on draft, free agency, trades
  * CRITICAL: Once playoffs start, most teams are ELIMINATED - do NOT feature eliminated teams

- For NFL:
  * Regular season: Sep-Jan
  * Wild Card/Divisional rounds: Mid-Late Jan (12 teams then 8 teams remain)
  * Conference Championships: Late Jan (only 4 teams remain - focus ONLY on these 4)
  * Super Bowl: Early Feb (only 2 teams remain - focus ONLY on these 2)
  * After Super Bowl: Offseason - focus on draft, free agency, coaching changes
  * CRITICAL: Most teams are ELIMINATED by late January - do NOT feature eliminated teams during playoffs

- For MLB:
  * Regular season: Apr-Sep
  * Wild Card: Early Oct (only 12 teams remain)
  * Division Series: Mid Oct (only 8 teams remain)
  * Championship Series: Late Oct (only 4 teams remain - focus ONLY on these 4)
  * World Series: Late Oct-Early Nov (only 2 teams remain - focus ONLY on these 2)
  * After World Series: Offseason - focus on free agency, trades, winter meetings
  * CRITICAL: Once playoffs start, most teams are ELIMINATED - do NOT feature eliminated teams

ELIMINATION RULES (APPLY TO ALL SPORTS):
- ABSOLUTELY DO NOT create narratives about teams that have been eliminated from playoff contention
- During playoffs: Focus EXCLUSIVELY on teams still competing in the current playoff round
- During championship rounds: Focus ONLY on the teams playing for the championship
- During offseason: Focus on draft, trades, roster moves, or season retrospectives
- Ensure narratives have CURRENT RELEVANCE - completely avoid discussing teams whose season has ended
- Prioritize teams that are actively competing or have upcoming games THIS WEEK

NARRATIVE VARIETY AND CONTENT REQUIREMENTS:
- CRITICAL: Ensure the 5 narratives cover a VARIETY of content types - do NOT focus too heavily on any single type
- Content types to mix: player performances, team trends, power rankings, playoff races, trade rumors/deadlines, statistical analysis
- PRIORITY ORDER (most important to least important):
  1. CHAMPIONSHIPS, FINALS, SUPER BOWL - if happening this week, prioritize these above all else
  2. PLAYOFFS, TOURNAMENT GAMES - teams actively competing in elimination games
  3. PLAYOFF RACES - teams fighting for playoff spots during regular season
  4. STANDOUT PERFORMANCES - individual player achievements, scoring races, MVP candidates
  5. POWER RANKINGS, TEAM TRENDS - analysis of top teams and their trajectories
  6. TRADE DEADLINES, ROSTER MOVES - only include 1-2 narratives maximum on this topic
- Avoid creating multiple narratives about the same event type (e.g., don't create 3 trade deadline narratives)
- Prefer exciting, game-focused content over administrative/roster news

CRITICAL REQUIREMENTS FOR DATA POINTS:
1. You MUST cite EXACT numerical values from the chart data above (e.g., "118.5 offensive rating", "22 turnover differential", "-4.5 point spread")
2. Each dataPoint must reference a SPECIFIC value from a chart's dataPoints array - NO generic statements
3. The "context" field MUST compare to neighboring teams/players using their actual values from the chart (e.g., "Ranks between Cleveland (119.2) and Denver (117.8)")
4. AVOID superficial values - cite precise metrics that directly support the narrative
5. NEVER use generic adjectives in context (no "elite", "impressive", "dominant", "struggling") - only factual comparisons

CHART HIGHLIGHTS REQUIREMENTS:
- For each chart referenced, specify what should be highlighted when displayed in a mobile app
- Use highlightType: "player" for player names (e.g., ["Connor McDavid", "Nathan MacKinnon"])
- Use highlightType: "team" for team abbreviations (e.g., ["BOS", "MIL", "CHI"])
- Use highlightType: "conference" for conferences (e.g., ["Eastern", "Western"])
- Use highlightType: "division" for divisions (e.g., ["Atlantic", "Central"])
- Values should match the exact labels/abbreviations from the chart data

EXAMPLES OF GOOD vs BAD DATA POINTS:
✓ GOOD: "metric": "Offensive Rating", "value": "118.5 (5th in NBA)", "context": "Ranks between Cleveland (119.2) and Denver (117.8) in offensive efficiency"
✗ BAD: "metric": "Offensive Rating", "value": "118.5 (5th in NBA)", "context": "Elite efficiency despite injury concerns"

✓ GOOD: "metric": "Win Percentage", "value": ".682 (3rd in East)", "context": "3 games behind Boston (.727) but 2 games ahead of Milwaukee (.659)"
✗ BAD: "metric": "Team Performance", "value": "Playing well", "context": "Good recent form"

✓ GOOD: "metric": "Turnover Differential", "value": "+22 (1st in NFL)", "context": "6 turnovers ahead of 2nd-place Buffalo (+16) and 9 ahead of Kansas City (+13)"
✗ BAD: "metric": "Ball Security", "value": "Strong", "context": "Taking care of the ball"

CONTEXT FIELD REQUIREMENTS:
- Context MUST reference specific neighboring values from the chart data
- Include the teams/players immediately above and below in the ranking
- Cite their actual numerical values for comparison
- Include rankings (e.g., 5th in the league), percentiles (e.g., top 10 percent), or league-wide comparisons (e.g., league average is 108.5)
- Format example: 5th in the NBA, between Cleveland (119.2) and Denver (117.8), well above league average of 112.3
- AVOID generic phrases like "elite", "strong", "impressive", "dominant", "struggling"
- AVOID commentary or opinion - stick to factual comparisons from the data

LINK QUALITY REQUIREMENTS:
- CRITICAL: "links" should ALWAYS be analytical articles/blog posts that provide in-depth analysis of the narrative
- NEVER use chart data sources (Basketball-Reference, Hockey-Reference) as links unless they have analysis/commentary
- Links must add value beyond just displaying stats - they should provide expert opinions, insights, or perspectives
- Only use reputable links from these domains:
%s

- LINK PREFERENCE ORDER (most preferred to least):
  1. OPINION PIECES & ANALYSIS - analytical articles, hot takes, expert opinions (FiveThirtyEight, The Ringer, Substack writers)
  2. BLOG POSTS & COMMENTARY - independent writers, team blogs, analytics-focused content (Substack, SBNation, team blogs)
  3. NEWS ARTICLES WITH ANALYSIS - sports journalism with insights (The Athletic, ESPN features, SI analysis)
  4. AVOID PURE STATS SITES - do NOT link to Basketball-Reference, Hockey-Reference unless they have analytical content
- AVOID clickbait, aggregator sites, spam domains, and low-quality content
- Prefer links that discuss analytics, advanced metrics, or provide unique insights and perspectives
- CRITICAL: Prioritize RECENT articles (prefer articles from the last 7-30 days when possible)
- Links can reference the same URL/article if discussing different aspects or teams
- Use diverse links across all 5 narratives to provide variety and breadth of coverage

For each narrative:
1. Find relevant RECENT analytical articles or blog posts from ALLOWED DOMAINS ONLY (prioritize articles from the last 7-30 days)
2. Connect the news/analysis to SPECIFIC NUMERICAL insights from the charts
3. Extract EXACT values from the chart dataPoints (numbers, rankings, percentages)
4. Provide links to articles with in-depth analysis, expert opinions, or unique perspectives (NOT just stat pages)
5. Verify the team/player featured is CURRENTLY RELEVANT (not eliminated, has upcoming games, or is in current playoff round)

Return ONLY a JSON object in this exact format (no markdown, no extra text):
{
  "date": "%s",
  "narratives": [
    {
      "title": "Compelling narrative title",
      "summary": "2-3 sentence summary connecting news to specific chart insights with numerical evidence",
      "chartEvidence": [
        {
          "chartName": "dev/nhl__team_efficiency.json",
          "chartUrl": "https://d2jyizt5xogu23.cloudfront.net/dev/nhl__team_efficiency.json",
          "relevance": "Specific reason this chart's data supports the narrative"
        }
      ],
      "highlights": [
        {
          "chartName": "dev/nhl__team_efficiency.json",
          "highlightType": "team",
          "values": ["BOS", "TOR", "CAR"]
        },
        {
          "chartName": "dev/nhl__player_scoring.json",
          "highlightType": "player",
          "values": ["Connor McDavid", "Nathan MacKinnon"]
        }
      ],
      "dataPoints": [
        {
          "metric": "Exact metric name from chart (e.g., 'Offensive Rating', 'Turnover Differential')",
          "value": "SPECIFIC numerical value with context (e.g., '118.5 (5th in NBA)', '+22 (1st in NFL)')",
          "context": "Factual comparison to neighboring teams/players in ranking (e.g., 'Between Cleveland (119.2) and Denver (117.8)')"
        }
      ],
      "links": [
        {
          "title": "Article or blog post title with analysis/opinion",
          "url": "https://... (MUST be analytical content from allowed domains, NOT just stat pages)",
          "type": "news" or "blog"
        }
      ]
    }
  ]
}

Focus on:
- NHL, NBA, NFL, MLB, or Soccer depending on available charts
- Advanced analytics insights (offensive/defensive ratings, efficiency metrics, expected goals, EPA, etc.)
- Recent news that the SPECIFIC NUMERICAL DATA helps explain or predict
- Quality analytics content from Substack, FiveThirtyEight, The Ringer, PFF, team blogs, etc.""" today chartsContext today allowedDomainsString timestamp

    // Create request for Gemini with grounded search
    let request = {
        contents = [
            {
                parts = [
                    { text = prompt }
                ]
            }
        ]
        tools = [
            {
                google_search = obj()  // Enable Google Search grounding
            }
        ]
        generationConfig = {
            temperature = 0.7
            maxOutputTokens = 8000
        }
    }

    let requestJson = JsonSerializer.Serialize request

    // Call Gemini API
    use httpClient = new HttpClient()
    httpClient.Timeout <- TimeSpan.FromMinutes(2.0)
    // Using gemini-2.0-flash (stable 2.0 model with grounding support)
    let apiUrl = sprintf "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=%s" apiKey

    let content = new StringContent(requestJson, Encoding.UTF8, "application/json")
    let! response = httpClient.PostAsync(apiUrl, content) |> Async.AwaitTask

    let! responseBody = response.Content.ReadAsStringAsync() |> Async.AwaitTask

    if not response.IsSuccessStatusCode then
        eprintfn "Gemini API Error Response:"
        eprintfn "%s" responseBody
        failwith (sprintf "Gemini API request failed with status %d" (int response.StatusCode))

    // Parse response
    let geminiResponse = JsonSerializer.Deserialize<GeminiResponse> responseBody

    let responseText =
        geminiResponse.candidates
        |> List.tryHead
        |> Option.bind (fun candidate ->
            candidate.content.parts
            |> List.tryHead
            |> Option.map (fun part -> part.text))
        |> Option.defaultValue ""

    // Clean up the response (remove markdown code blocks if present)
    let cleanedResponse =
        responseText
            .Replace("```json", "")
            .Replace("```", "")
            .Trim()

    // Parse and return the sports narratives response
    try
        let narratives = JsonSerializer.Deserialize<SportsNarrativesResponse> cleanedResponse
        return narratives
    with ex ->
        eprintfn "JSON Parsing Error:"
        eprintfn "%s" ex.Message
        eprintfn ""
        eprintfn "Raw Gemini Response (first 2000 chars):"
        eprintfn "%s" (cleanedResponse.Substring(0, min 2000 cleanedResponse.Length))
        eprintfn ""
        eprintfn "Response length: %d characters" cleanedResponse.Length
        return! failwith (sprintf "Failed to parse Gemini response: %s" ex.Message)
}

[<EntryPoint>]
let main argv =
    try
        // Get S3 bucket from environment
        let s3Bucket = Environment.GetEnvironmentVariable "AWS_S3_BUCKET"
        if String.IsNullOrEmpty s3Bucket then
            failwith "S3_BUCKET environment variable not set"

        printfn "Fetching sports narratives using chart data and Gemini with grounded search..."
        printfn ""

        let narratives = getSportsNarratives () |> Async.RunSynchronously

        // Serialize to pretty JSON
        let options = new JsonSerializerOptions()
        options.WriteIndented <- true
        let json = JsonSerializer.Serialize(narratives, options)

        printfn "\x1b[32m✓ Successfully retrieved %d narratives\x1b[0m" narratives.Narratives.Length

        // Upload to S3
        uploadToS3 s3Bucket json |> Async.RunSynchronously

        printfn "\x1b[32m✓ Fastbreak.Daily completed successfully\x1b[0m"
        0 // Success
    with
    | ex ->
        eprintfn "\x1b[31m✗ Error: %s\x1b[0m" ex.Message
        1 // Error
