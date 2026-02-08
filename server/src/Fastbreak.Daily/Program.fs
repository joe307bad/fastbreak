open System
open System.IO
open System.Net.Http
open System.Text
open System.Text.Json
open System.Text.Json.Serialization
open Amazon
open Amazon.DynamoDBv2
open Amazon.DynamoDBv2.Model
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
    [<JsonPropertyName("league")>]
    League: string  // "nba", "nfl", "nhl", "mlb", "mls"
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

    printfn "Found %d entries in registry" registry.Count

    // Filter out topics entries (type = "topics")
    let chartEntries =
        registry
        |> Seq.filter (fun kvp ->
            match kvp.Value.TryGetProperty("type") with
            | true, typeProp ->
                let typeValue = typeProp.GetString()
                typeValue <> "topics"
            | false, _ -> true  // No type property means it's a chart
        )
        |> Seq.map (fun kvp -> kvp.Key)
        |> Seq.toList

    printfn "Found %d charts (excluding topics)" chartEntries.Length

    // Download all chart JSONs
    let! chartDataTasks =
        chartEntries
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

let getAwsRegion () =
    match Environment.GetEnvironmentVariable "AWS_REGION" with
    | null | "" ->
        match Environment.GetEnvironmentVariable "AWS_DEFAULT_REGION" with
        | null | "" -> RegionEndpoint.USEast1
        | r -> RegionEndpoint.GetBySystemName(r)
    | r -> RegionEndpoint.GetBySystemName(r)

let uploadToS3 (bucketName: string) (json: string) = async {
    let region = getAwsRegion()
    let s3Key = "dev/topics.json"

    use s3Client = new AmazonS3Client(region)

    let request = PutObjectRequest()
    request.BucketName <- bucketName
    request.Key <- s3Key
    request.ContentType <- "application/json"
    request.ContentBody <- json

    let! response = s3Client.PutObjectAsync(request) |> Async.AwaitTask
    printfn "Uploaded to s3://%s/%s (HTTP %d)" bucketName s3Key (int response.HttpStatusCode)
    return s3Key
}

let updateDynamoDB (s3Key: string) (title: string) = async {
    let tableName =
        match Environment.GetEnvironmentVariable "AWS_DYNAMODB_TABLE" with
        | null | "" -> "fastbreak-file-timestamps"
        | t -> t

    let region = getAwsRegion()
    use dynamoClient = new AmazonDynamoDBClient(region)

    let timestamp = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
    let interval = "daily"

    let item = System.Collections.Generic.Dictionary<string, AttributeValue>()
    item.["file_key"] <- AttributeValue(S = s3Key)
    item.["updatedAt"] <- AttributeValue(S = timestamp)
    item.["title"] <- AttributeValue(S = title)
    item.["interval"] <- AttributeValue(S = interval)
    item.["type"] <- AttributeValue(S = "topics")

    let request = PutItemRequest(TableName = tableName, Item = item)

    try
        let! _ = dynamoClient.PutItemAsync(request) |> Async.AwaitTask
        printfn "Updated DynamoDB: %s key: %s updatedAt: %s title: %s interval: %s type: topics" tableName s3Key timestamp title interval
    with ex ->
        eprintfn "Warning: Failed to update DynamoDB timestamp (non-fatal): %s" ex.Message
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

FACTUAL ACCURACY AND TIMELINESS (PARAMOUNT IMPORTANCE):
- It is of PARAMOUNT IMPORTANCE that all narratives are FACTUALLY ACCURATE and based on RECENT, VERIFIED information
- ALWAYS verify current playoff brackets, standings, and team matchups using web search before writing ANY narrative
- For championship games (Super Bowl, NBA Finals, Stanley Cup, World Series): VERIFY which teams are actually playing - do NOT guess or rely on outdated information
- Cross-reference multiple recent sources to confirm facts before including them
- If you cannot verify current information with high confidence, do NOT include that narrative
- Prefer narratives about events you can CONFIRM happened within the last 7 days
- NEVER assume playoff matchups or championship participants - ALWAYS verify with current web search results

MANDATORY ELIMINATION CHECK (DO THIS FIRST):
- BEFORE writing ANY narrative, you MUST search the web to verify which teams are CURRENTLY ACTIVE in each sport
- For NFL: After the Super Bowl (early February), the NFL season is OVER - do NOT write about "playoff success" or "championship hopes" for ANY team
- For NBA/NHL: During playoffs, MOST teams are eliminated - verify which teams are still playing BEFORE featuring them
- REJECTED EXAMPLES of outdated narratives:
  * "Bills keys to playoff success" in February = WRONG (Bills were eliminated in January)
  * "Lakers championship odds" in May = WRONG if Lakers are eliminated
  * "Cowboys playoff push" after they've been eliminated = WRONG
- If a team's season has ended (eliminated or season over), you may ONLY discuss:
  * Offseason moves (trades, free agency, draft)
  * Retrospective analysis ("how their season went")
  * Future outlook ("what to expect next season")
- DO NOT write forward-looking competitive narratives ("keys to success", "playoff chances") for teams whose season is over

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
- THIS IS A HARD RULE: If a team lost in the playoffs, their season is OVER - do not feature them
- During playoffs: Focus EXCLUSIVELY on teams still competing in the current playoff round
- During championship rounds: Focus ONLY on the teams playing for the championship
- During offseason: Focus on draft, trades, roster moves, or season retrospectives
- Ensure narratives have CURRENT RELEVANCE - completely avoid discussing teams whose season has ended
- Prioritize teams that are actively competing or have upcoming games THIS WEEK
- VERIFICATION STEP: Before including ANY team in a narrative, ask yourself: "Is this team's season still active?"
  * If YES (still playing, not eliminated) -> OK to feature
  * If NO (eliminated, season over) -> DO NOT feature in competitive narratives
- Examples of teams to EXCLUDE after elimination:
  * NFL: Any team not in the Super Bowl after Conference Championships
  * NBA/NHL: Any team eliminated from current playoff round
  * MLB: Any team eliminated from postseason

NARRATIVE VARIETY AND CONTENT REQUIREMENTS:
- CRITICAL: Ensure the 5 narratives cover a VARIETY of content types - do NOT focus too heavily on any single type
- Content types to mix: player performances, team trends, power rankings, playoff races, trade rumors/deadlines, statistical analysis

MANDATORY CHAMPIONSHIP CHECK (DO THIS BEFORE ANYTHING ELSE):
- BEFORE writing narratives, SEARCH THE WEB to check if ANY league is currently in playoffs or has a championship game coming up this week
- NFL: Is the Super Bowl this week or next week? If so, you MUST include at least one narrative about it
- NBA: Are the NBA Finals happening? Are conference finals happening? Include narratives about teams still competing
- NHL: Is the Stanley Cup Finals happening? Are conference finals happening? Include narratives about teams still competing
- MLB: Is the World Series happening? Are the LCS series happening? Include narratives about teams still competing
- If a championship game (Super Bowl, NBA Finals, Stanley Cup Finals, World Series) is within 7 days, AT LEAST ONE narrative MUST be about that matchup
- This is NON-NEGOTIABLE - championship games are the biggest events in sports and MUST be covered

- PRIORITY ORDER (most important to least important):
  1. CHAMPIONSHIPS, FINALS, SUPER BOWL - if happening this week, prioritize these above all else
  2. PLAYOFFS, TOURNAMENT GAMES - teams actively competing in elimination games
  3. PLAYOFF RACES - teams fighting for playoff spots during regular season

SUMMARY WRITING STYLE (CONCISENESS IS KEY):
- Be CONCISE - when in doubt, use fewer words
- AVOID redundant and obvious commentary - let the stats speak for themselves
- DO NOT explain what a stat means if it's self-evident
- REJECTED EXAMPLES of verbose/obvious writing:
  * "Player X has Y stat which showcases their offensive prowess" = TOO OBVIOUS
  * "This demonstrates their defensive excellence" = REDUNDANT
  * "These numbers highlight their dominance" = FILLER
  * "The team's strong performance is evidenced by..." = WORDY
- GOOD EXAMPLES of concise writing:
  * "Player X leads the league with Y stat, ahead of Player Z"
  * "Team A ranks 2nd in offensive rating heading into the playoffs"
  * "The matchup features the top two defenses in the conference"
- The summary should add CONTEXT or INSIGHT, not just restate what the numbers already show
- Focus on: matchups, implications, trends, comparisons - NOT obvious interpretations
- Maximum 2-3 sentences for the summary - shorter is better
  4. STANDOUT PERFORMANCES - individual player achievements, scoring races, MVP candidates
  5. POWER RANKINGS, TEAM TRENDS - analysis of top teams and their trajectories
  6. TRADE DEADLINES, ROSTER MOVES - only include 1-2 narratives maximum on this topic
- Avoid creating multiple narratives about the same event type (e.g., don't create 3 trade deadline narratives)
- Prefer exciting, game-focused content over administrative/roster news

CRITICAL REQUIREMENTS FOR DATA POINTS:
1. You MUST cite EXACT numerical values from the chart data above (e.g., "118.5 offensive rating", "22 turnover differential", "-4.5 point spread")
2. Each dataPoint must reference a SPECIFIC value from a chart's dataPoints array - NO generic statements
3. The "context" field MUST frame stats in terms of LEAGUE-WIDE STANDING using tiers like "top quarter", "top third", "bottom half", "top 5", "middle of the pack"
4. AVOID superficial values - cite precise metrics that directly support the narrative
5. NEVER use generic adjectives in context (no "elite", "impressive", "dominant", "struggling") - only factual league positioning
6. ALWAYS use web search to verify current standings and recent performance before citing stats

CHART HIGHLIGHTS REQUIREMENTS:
- For each chart referenced, specify what should be highlighted when displayed in a mobile app
- Use highlightType: "player" for player names (e.g., ["Connor McDavid", "Nathan MacKinnon"])
- Use highlightType: "team" for team abbreviations (e.g., ["BOS", "MIL", "CHI"])
- Use highlightType: "conference" for conferences (e.g., ["Eastern", "Western"])
- Use highlightType: "division" for divisions (e.g., ["Atlantic", "Central"])
- Values should match the exact labels/abbreviations from the chart data

EXAMPLES OF GOOD vs BAD DATA POINTS:
✓ GOOD: "metric": "Offensive Rating", "value": "118.5 (5th in NBA)", "context": "The Boston Celtics post an offensive rating of 118.5, ranking 5th in the NBA and placing them in the top quarter of the league. They trail only the Cleveland Cavaliers (119.2) and Oklahoma City Thunder (119.0) among Eastern Conference contenders."
✗ BAD: "metric": "Offensive Rating", "value": "118.5", "context": "Places them in the top quarter" (missing team name, incomplete sentence)
✗ BAD: "metric": "Offensive Rating", "value": "118.5", "context": "This is more than Charlotte who has 105.2" (irrelevant comparison, no team name for subject)

✓ GOOD: "metric": "Win Percentage", "value": ".682 (3rd in East)", "context": "The New York Knicks hold a .682 win percentage, ranking 3rd in the Eastern Conference. They trail only the Boston Celtics (.727) and Milwaukee Bucks (.695), positioning them as a top-tier playoff contender."
✗ BAD: "metric": "Win Percentage", "value": ".682", "context": "Top third in the conference" (no team name, not a complete sentence)

✓ GOOD: "metric": "Turnover Differential", "value": "+22 (1st in NFL)", "context": "The Philadelphia Eagles lead the NFL with a +22 turnover differential, the best in the league by a significant margin. The Buffalo Bills rank 2nd at +16, highlighting Philadelphia's exceptional ball security."
✗ BAD: "metric": "Turnover Differential", "value": "+22", "context": "Best in the league" (no team name, incomplete)

✓ GOOD: "metric": "Points Per Game", "value": "32.4 (2nd in NFL)", "context": "The Kansas City Chiefs average 32.4 points per game, ranking 2nd in the NFL and placing them in the top 10%% of league offenses. Only the Detroit Lions (33.1) score more among playoff contenders."
✗ BAD: "metric": "Points Per Game", "value": "32.4", "context": "Higher than the Bears who score 18.2" (irrelevant comparison, no subject team)

HIGH-QUALITY CONTEXT REQUIREMENTS:
- CRITICAL: The "context" field must be a FULL, COHERENT SENTENCE that a reader can understand without seeing other fields
- ALWAYS include the FULL TEAM NAME in the context (e.g., "The Boston Celtics rank 3rd..." NOT just "Ranks 3rd...")
- Context MUST frame the stat in terms of LEAGUE-WIDE POSITION (e.g., "top quarter of the league", "ranks in the top 5", "middle third of the conference")
- When referencing other teams for comparison, ALWAYS use their full names (e.g., "The Cleveland Cavaliers lead at 119.2" NOT just "Cleveland (119.2)")
- NEVER compare to random low-performing teams - only compare to teams DIRECTLY ABOVE or BELOW in the same ranking tier
- Use phrases like: "top quarter", "top third", "top half", "bottom third", "league-leading", "middle of the pack", "trails only X and Y"
- Include the RANKING NUMBER (e.g., "3rd in the East", "7th in the NFL", "top 5 in the league")
- When comparing, explain WHY the comparison matters (e.g., "trails only playoff-bound teams", "ahead of all wild card contenders")
- AVOID arbitrary comparisons to teams that are not relevant (e.g., don't compare the 3rd place team to the 28th place team)
- Format example: "The Boston Celtics rank 5th in the NBA in offensive rating at 118.5, placing them in the top quarter of the league. They trail only the Cleveland Cavaliers (119.2) and Denver Nuggets (117.8), with all three teams holding top-4 seeds in their conferences."
- AVOID generic phrases like "elite", "strong", "impressive", "dominant", "struggling"
- AVOID commentary or opinion - stick to factual league positioning and meaningful tier comparisons
- Each context should be SELF-CONTAINED and readable as a standalone sentence explaining the statistic

LINK QUALITY REQUIREMENTS:
- CRITICAL: Each narrative MUST include AT LEAST 2 relevant links - this is a hard requirement
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

LEAGUE FIELD REQUIREMENTS:
- Each narrative MUST include a "league" field indicating which sport/league the narrative is about
- Use lowercase values: "nba", "nfl", "nhl", "mlb", or "mls"
- The league should match the primary sport discussed in the narrative

For each narrative:
1. Find relevant RECENT analytical articles or blog posts from ALLOWED DOMAINS ONLY (prioritize articles from the last 7-30 days)
2. Connect the news/analysis to SPECIFIC NUMERICAL insights from the charts
3. Extract EXACT values from the chart dataPoints (numbers, rankings, percentages)
4. Provide links to articles with in-depth analysis, expert opinions, or unique perspectives (NOT just stat pages)
5. Verify the team/player featured is CURRENTLY RELEVANT (not eliminated, has upcoming games, or is in current playoff round)
6. Include the correct "league" field for the sport being discussed

Return ONLY a JSON object in this exact format (no markdown, no extra text):
{
  "date": "%s",
  "narratives": [
    {
      "title": "Compelling narrative title",
      "summary": "2-3 sentence summary connecting news to specific chart insights with numerical evidence",
      "league": "nba",
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
          "context": "FULL COHERENT SENTENCE with team name (e.g., 'The Boston Celtics post an offensive rating of 118.5, ranking 5th in the NBA. They trail the Cleveland Cavaliers (119.2) and Denver Nuggets (117.8) among top seeds.')"
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
        let s3Key = uploadToS3 s3Bucket json |> Async.RunSynchronously

        // Update DynamoDB with timestamp
        let title = sprintf "Sports Topics - %s" (DateTime.UtcNow.ToString("yyyy-MM-dd"))
        updateDynamoDB s3Key title |> Async.RunSynchronously

        printfn "\x1b[32m✓ Fastbreak.Daily completed successfully\x1b[0m"
        0 // Success
    with
    | ex ->
        eprintfn "\x1b[31m✗ Error: %s\x1b[0m" ex.Message
        1 // Error
