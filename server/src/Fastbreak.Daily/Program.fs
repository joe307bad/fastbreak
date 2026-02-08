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
    [<JsonPropertyName("source")>]
    Source: string  // "chart" or "web"
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

// Grounding metadata types for extracting real URLs from Google Search
type WebChunk = {
    uri: string
    title: string
}

type GroundingChunk = {
    web: WebChunk option
}

type GroundingMetadata = {
    groundingChunks: GroundingChunk list option
}

type GeminiCandidate = {
    content: GeminiContent
    groundingMetadata: GroundingMetadata option
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

=== STEP 1: IDENTIFY MAJOR SPORTING EVENTS THIS WEEK ===

Search the web to identify any CHAMPIONSHIP or PLAYOFF games happening THIS WEEK:
- Super Bowl, NBA Finals, Stanley Cup Finals, World Series
- Conference championships, playoff rounds, elimination games
- If a major event is happening, you MUST include exactly 1 narrative about it (not more)

=== STEP 2: SEARCH FOR INTERESTING STORYLINES AND TALKING POINTS ===

This is the most important step. Search the web extensively for INTERESTING, SUBSTANTIVE sports storylines. Look for:
- Surprising statistical trends that challenge conventional wisdom
- Controversial takes or debates among analysts
- Emerging players having breakout seasons
- Teams defying expectations (overperforming or underperforming)
- Historical context that makes current events more meaningful
- Trade deadline implications, contract situations, front office drama
- Injury impacts and how teams are adapting
- Coaching decisions being questioned or praised
- Advanced analytics insights that casual fans might not know

DO NOT settle for surface-level observations like "Team X leads the league in Y." Instead, find the STORY behind the numbers. Ask "why?" and "what does this mean?"

EXAMPLES OF SURFACE-LEVEL (BAD) vs SUBSTANTIVE (GOOD) NARRATIVES:
- BAD: "The Celtics lead the league in 3-point shooting" (boring, obvious)
- GOOD: "The Celtics' 3-point revolution is being questioned after playoff struggles - their league-leading 42%% regular season rate dropped to 34%% in clutch playoff moments, reigniting the 'live by the three, die by the three' debate"

- BAD: "Patrick Mahomes has the most passing yards" (who cares?)
- GOOD: "Mahomes is having a statistically quieter season but his EPA on 3rd down has actually increased 15%% - is he becoming MORE efficient while throwing LESS?"

- BAD: "Connor McDavid leads the NHL in points" (everyone knows this)
- GOOD: "McDavid's dominance is so extreme that advanced models are breaking - his expected goals vs actual goals gap suggests he's literally playing a different sport than everyone else"

=== STEP 3: MATCH CHART DATA TO SUBSTANTIVE STORYLINES ===

I have the following sports analytics charts available:

%s

For each interesting storyline from Step 2, find chart data that provides ANALYTICAL DEPTH. The charts should SUPPORT and QUANTIFY the narrative, not drive it.

=== STEP 4: CREATE 5 NARRATIVES ===

Narrative allocation:
- 1 narrative for any major event (championship/playoff game) if happening this week
- 3-4 narratives for substantive, interesting storylines discovered in Step 2
- Each narrative MUST have a unique angle - NO DUPLICATE TOPICS OR TEAMS

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

NO DUPLICATE NARRATIVES - HARD RULE:
- Each narrative MUST cover a DIFFERENT topic, team, or angle
- Even for major events like the Super Bowl: include exactly 1 narrative, not 2
- Do NOT create multiple narratives about the same game, team, or storyline
- Variety is essential - spread narratives across different sports and topics

MAJOR EVENT COVERAGE:
- If a championship game (Super Bowl, NBA Finals, etc.) is happening this week: exactly 1 narrative
- The remaining 4 narratives should cover OTHER interesting storylines from different sports/teams
- Major events are important but should not dominate - fans want variety

SUMMARY WRITING STYLE (NARRATIVE-FOCUSED, FROM WEB SEARCH):
- Summaries should come from your GROUNDED WEB SEARCH - tell the STORY, not just stats
- Focus on: storylines, narratives, context, historical significance, debates, talking points
- The summary sets up the narrative; the data points provide the statistical evidence
- DO NOT repeat information that will appear in the data points - each piece of content should be UNIQUE

*** SUMMARY vs DATA POINTS - NO REPETITION ***
- SUMMARY: Narrative context from web search (storylines, history, debates, implications)
- DATA POINTS: Statistical evidence from charts and web (numbers, rankings, comparisons)
- These should COMPLEMENT each other, NOT repeat the same information
- If a stat appears in the summary, do NOT repeat it in data points (and vice versa)

BANNED from summaries:
- Fluff adjectives: "impressive", "dominant", "elite", "struggling", "exceptional", "remarkable"
- Opinion phrases: "showcases", "demonstrates", "highlights", "poised to", "looks to"
- Stats that will be repeated in data points (avoid redundancy)

GOOD SUMMARY EXAMPLES (narrative-focused, from web search):
- "Super Bowl LIX marks the first rematch since Super Bowl LVII, with both teams taking different paths - Kansas City through dynasty continuity, Philadelphia through a complete roster overhaul. The Eagles are 2.5-point favorites, the largest spread against a defending champion since 2016."
- "The NBA trade deadline is 48 hours away and the Lakers are reportedly 'all-in' on a Jimmy Butler deal. Miami is demanding two first-round picks, while LA is offering one plus role players. Butler hasn't played since January 4th due to a suspension."
- "Connor McDavid's chase for 70 goals has captivated hockey - he'd be the first since Lemieux in 1996. The Oilers have restructured their entire offense around his zone entries, with teammates instructed to clear space rather than join the rush."

BAD SUMMARY EXAMPLES (too stats-heavy, repetitive with data points):
- "The Chiefs rank 2nd in EPA per play and 4th in red zone TD rate" (these stats belong in data points)
- "Boston shoots 39.2%% from three, ranking 1st in the NBA" (save for data points)

- Maximum 2-3 sentences focused on NARRATIVE, not statistics

===== MANDATORY DATA POINTS - THIS IS A HARD REQUIREMENT =====

EVERY NARRATIVE MUST HAVE EXACTLY 3 DATA POINTS. THIS IS NON-NEGOTIABLE.
- 2 data points MUST come from the CHARTS provided above (cite exact values from chart data)
- 1 data point MUST come from your GROUNDED WEB SEARCH (fresh stats not in the charts)
- If a narrative has fewer than 3 data points, it is INVALID and must be fixed

CRITICAL REQUIREMENTS FOR DATA POINTS:
1. CHART DATA POINTS (2 required): Cite EXACT numerical values from the chart data above (e.g., "118.5 offensive rating", "+22 turnover differential", "42.3%% three-point percentage")
2. WEB SEARCH DATA POINT (1 required): Find a relevant stat from your grounded web search that adds context not available in the charts (e.g., recent game performance, historical comparison, betting lines, injury impact)

*** USE DIFFERENT CHARTS - NO REPETITION ***
- The 2 chart data points should come from DIFFERENT charts when possible
- BAD: Both data points from "nfl__team_efficiency.json" (repetitive)
- GOOD: One from "nfl__team_efficiency.json", one from "nfl__team_scoring.json" (variety)
- Avoid repetitive tier descriptions - if one says "top quarter", the other should say something different (e.g., "1st in the league", "top 5", "bottom third")
- Each data point should reveal a DIFFERENT aspect of the team/player

*** SUBJECT RELEVANCE RULE - CRITICAL ***
ALL data points (chart and web) MUST be about the SUBJECTS mentioned in the narrative title.
- First, identify the subject(s) in the title (teams, players, matchup)
- Example: Title "Super Bowl LIX: Chiefs vs Eagles Matchup Preview" → subjects are Chiefs AND Eagles
- Example: Title "Celtics' Three-Point Shooting Revolution" → subject is Celtics
- Example: Title "McDavid's Historic Scoring Pace" → subject is Connor McDavid
- The 2 chart data points MUST cite stats about these subjects, NOT random other teams
- BAD: Title about Chiefs vs Eagles, but data points cite Bills and Ravens stats (IRRELEVANT)
- GOOD: Title about Chiefs vs Eagles, data points cite Chiefs offensive rating and Eagles defensive rating

3. MAIN SUBJECT FIRST - the first data point must be about the PRIMARY team/player in the narrative title. If the title is about the Eagles, the first stat must be about the Eagles.
3. LEAGUE-WIDE CONTEXT IS MANDATORY - every data point must include league positioning using these tier descriptors:
   * "1st/2nd/3rd in the league" (for top 3)
   * "top 5 in the league" (for 4th-5th)
   * "top quarter of the league" (for top 25%%)
   * "top third of the league" (for top 33%%)
   * "top half of the league" (for top 50%%)
   * "bottom half of the league" (for bottom 50%%)
   * "bottom third of the league" (for bottom 33%%)
   * "bottom quarter of the league" (for bottom 25%%)
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

DATA POINT ORDER: First data point MUST be about the main subject in the title.
Example: If title is "Eagles' Turnover Edge Could Decide Super Bowl" - first data point must be about the Eagles.

*** NO REDUNDANCY RULE ***
Use EITHER a specific ranking (1st, 2nd, 5th) OR a tier description (top quarter), NEVER BOTH.
- BAD: "ranking 2nd in the NFL and in the top quarter of the league" (redundant - 2nd IS top quarter)
- BAD: "ranking 5th in the NBA and placing them in the top 10%%" (redundant)
- GOOD: "ranking 2nd in the NFL" (specific rank, no tier needed)
- GOOD: "in the top quarter of the league" (tier only, when exact rank is less important)

✓ GOOD: "metric": "Offensive Rating", "value": "118.5 (5th in NBA)", "context": "The Boston Celtics post an offensive rating of 118.5, ranking 5th in the NBA. They trail Cleveland (119.2) and Oklahoma City (119.0) but have been the league's best since the trade deadline. Their halfcourt efficiency has been particularly devastating, converting at 1.12 points per possession."
✗ BAD: "metric": "Offensive Rating", "value": "118.5", "context": "Places them in the top quarter" (too short, missing team name)
✗ BAD: "metric": "Offensive Rating", "value": "118.5", "context": "The Lakers struggle offensively" (WRONG TEAM - narrative is about Celtics!)

✓ GOOD: "metric": "Win Percentage", "value": ".682 (3rd in East)", "context": "The New York Knicks hold a .682 win percentage, 3rd in the Eastern Conference behind Boston (.727) and Milwaukee (.695). Their road record of 22-9 is the best in franchise history at this point in the season. The Knicks have won 8 of their last 10 against playoff teams."
✗ BAD: "metric": "Win Percentage", "value": ".682", "context": "The Heat are struggling" (WRONG TEAM - narrative is about Knicks!)

✓ GOOD: "metric": "Turnover Differential", "value": "+22 (1st in NFL)", "context": "The Philadelphia Eagles lead the NFL with a +22 turnover differential. Kansas City ranks 8th at +6, creating a 16-turnover gap between Super Bowl opponents. The Eagles have forced 31 turnovers this season, with their secondary accounting for 18 interceptions."
✗ BAD: "metric": "Turnover Differential", "value": "+22", "context": "The Bills had a great season" (WRONG TEAM - narrative is about Eagles!)

✓ GOOD: "metric": "Points Per Game", "value": "32.4 (2nd in NFL)", "context": "The Kansas City Chiefs average 32.4 points per game, 2nd in the NFL behind Detroit (33.1). Patrick Mahomes has thrown for 4,183 yards with a 67.5%% completion rate. Mahomes' red zone efficiency has been critical to the Chiefs' scoring dominance."
✗ BAD: "metric": "Points Per Game", "value": "32.4", "context": "The Ravens offense is dangerous" (WRONG TEAM - narrative is about Chiefs!)

HIGH-QUALITY CONTEXT REQUIREMENTS:
- CRITICAL: The "context" field must be 2-3 FULL SENTENCES with SUBSTANCE and INSIGHT
- ALWAYS include the FULL TEAM NAME in the context (e.g., "The Boston Celtics rank 3rd..." NOT just "Ranks 3rd...")
- NO REDUNDANCY: Use specific ranking OR tier description, not both (if you say "2nd in the NFL", don't add "top quarter")
- Include MEANINGFUL COMPARISONS to relevant teams (opponents, division rivals, or conference leaders)
- Add CONTEXT about what this stat means for the team's performance or chances
- Each data point context should tell a mini-story, not just state a number

CONTEXT FORMAT - 2-3 sentences following this structure:
"[Team name] [stat with value], [ranking]. [Comparison to relevant team]. [Insight about what this means]."

Example: "The Philadelphia Eagles lead the NFL with a +22 turnover differential. Kansas City ranks 8th at +6, creating a 16-turnover gap between Super Bowl opponents. The Eagles' ball security has been the foundation of their playoff run, with 4 interceptions in their last 3 games."

Example: "The Boston Celtics post a 118.5 offensive rating, 5th in the NBA. They trail Cleveland (119.2) and Oklahoma City (119.0) but have surged since the All-Star break. Jayson Tatum has averaged 32.1 points in February, and Tatum's efficiency has carried the offense."

NAME USAGE RULE:
- First mention of a player: use FULL NAME (e.g., "Patrick Mahomes", "Jayson Tatum", "Connor McDavid")
- Subsequent mentions: use LAST NAME ONLY (e.g., "Mahomes", "Tatum", "McDavid")
- Example: "Patrick Mahomes has thrown for 4,183 yards. Mahomes' red zone efficiency has been critical..."
- BAD: "Patrick Mahomes has thrown for 4,183 yards. Patrick Mahomes' red zone efficiency..." (repetitive)

BANNED from context (will be rejected):
- Adjectives: "elite", "impressive", "dominant", "exceptional", "remarkable", "strong", "struggling", "poor"
- Opinion phrases: "showcases", "demonstrates", "highlights", "proves", "cements"
- Vague comparisons: "one of the best", "among the top", "near the bottom"
- Redundant phrasing: "ranking Xth AND in the top Y%%" - pick ONE
- Citation markers: NEVER include "[cite X]", "[source]", "[chart name]", or any bracketed citations - just write the content directly

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

REMINDER: Each narrative MUST have exactly 3 data points: 2 from charts (source: "chart") + 1 from web search (source: "web"). This is mandatory.

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
          "metric": "Turnover Differential",
          "value": "+22 (1st in NFL)",
          "context": "The Philadelphia Eagles lead the NFL with a +22 turnover differential, ranking 1st in the league. The Kansas City Chiefs rank 8th at +6.",
          "source": "chart"
        },
        {
          "metric": "Points Per Game Allowed",
          "value": "18.2 (2nd in NFL)",
          "context": "The Philadelphia Eagles allow 18.2 points per game, ranking 2nd in the NFL and in the top 10%% of league defenses. Only the Baltimore Ravens (17.8) allow fewer.",
          "source": "chart"
        },
        {
          "metric": "Saquon Barkley Rushing Yards",
          "value": "2,005 (1st in NFL)",
          "context": "Saquon Barkley rushed for 2,005 yards in the regular season, becoming just the 9th player in NFL history to reach 2,000. He averaged 5.8 yards per carry, ranking 3rd among qualified rushers.",
          "source": "web"
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
- STATS-DRIVEN SUMMARIES: Lead with numbers, not adjectives. Let statistics speak for themselves.
- LEAGUE-WIDE CONTEXT: Every data point must include tier positioning (top quarter, bottom third, etc.)
- MAIN SUBJECT FIRST: First data point must be about the primary team/player in the title
- DATA POINT RELEVANCE: ALL data points MUST be about the teams/players mentioned in the narrative title and summary
- NO FLUFF: Zero tolerance for "impressive", "dominant", "elite", "exceptional" - just facts and numbers
- VARIETY: 5 different narratives covering different teams, sports, and angles
- MAJOR EVENTS: Exactly 1 narrative if a championship/playoff game is happening this week

FINAL CHECK BEFORE SUBMITTING (DO NOT SKIP):

*** ABSOLUTE REQUIREMENT - DATA POINT RELEVANCE ***
THIS IS THE MOST IMPORTANT RULE. VIOLATIONS WILL CAUSE THE ENTIRE RESPONSE TO BE REJECTED.

For EACH narrative, ALL 3 data points MUST be about the EXACT teams/players in that narrative's title:
- Narrative about "Chiefs vs Eagles" → ALL 3 data points must mention Chiefs, Eagles, Mahomes, Hurts, etc. - ZERO exceptions
- Narrative about "Celtics defense" → ALL 3 data points must be Celtics stats - NOT Lakers, NOT Warriors, NOT any other team
- Narrative about "Connor McDavid" → ALL 3 data points must be about McDavid or Oilers - NOT Crosby, NOT other players

BEFORE adding ANY data point, ask: "Does this stat mention a team/player from the narrative title?"
- If YES → include it
- If NO → DO NOT INCLUDE IT, find a different stat

COMMON MISTAKES TO AVOID:
- Narrative about Team A, but data point mentions Team B's stats → WRONG
- Narrative about Player X, but data point compares to Player Y without mentioning X → WRONG
- Using generic league stats that don't specifically name the narrative subject → WRONG

The data points exist to QUANTIFY and SUPPORT the specific narrative - they are NOT general league observations.

*** DATA POINT COUNT CHECK ***
Each narrative MUST have exactly 3 dataPoints: 2 from charts + 1 from web search.
- Narrative 1: Has 3 dataPoints (2 chart + 1 web)? All relevant to narrative subject? (REQUIRED)
- Narrative 2: Has 3 dataPoints (2 chart + 1 web)? All relevant to narrative subject? (REQUIRED)
- Narrative 3: Has 3 dataPoints (2 chart + 1 web)? All relevant to narrative subject? (REQUIRED)
- Narrative 4: Has 3 dataPoints (2 chart + 1 web)? All relevant to narrative subject? (REQUIRED)
- Narrative 5: Has 3 dataPoints (2 chart + 1 web)? All relevant to narrative subject? (REQUIRED)

Other checks:
- Is the SUMMARY narrative-focused (from web search) and NOT repeating stats from data points?
- Do the 2 chart data points come from DIFFERENT charts? (avoid using same chart twice)
- Are the tier descriptions varied? (not both saying "top quarter")
- Does each dataPoint have a "source" field ("chart" or "web")?
- Does the FIRST data point cite the main subject from the title?
- Are summaries FREE of fluff adjectives?
- Are all 5 narratives about DIFFERENT topics/teams?""" today chartsContext today allowedDomainsString timestamp

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
    let options = JsonSerializerOptions()
    options.PropertyNameCaseInsensitive <- true
    let geminiResponse = JsonSerializer.Deserialize<GeminiResponse>(responseBody, options)

    let candidate = geminiResponse.candidates |> List.tryHead

    let responseText =
        candidate
        |> Option.bind (fun c ->
            c.content.parts
            |> List.tryHead
            |> Option.map (fun part -> part.text))
        |> Option.defaultValue ""

    // Extract grounding URLs from the response metadata
    // Exclude Wikipedia and prioritize quality sources
    let excludedDomains = [
        "wikipedia.org"; "wikimedia.org"; "en.wikipedia"; "en.m.wikipedia"
        "wikidata.org"; "wikiquote.org"; "wiktionary.org"
    ]
    let priorityPublications = [
        "theathletic.com"; "espn.com"; "si.com"; "nytimes.com"; "washingtonpost.com"
        "bleacherreport.com"; "cbssports.com"; "nbcsports.com"; "foxsports.com"
    ]
    let statSites = [
        "statmuse.com"; "pro-football-reference.com"; "basketball-reference.com"
        "hockey-reference.com"; "baseball-reference.com"; "fangraphs.com"
        "footballoutsiders.com"; "pff.com"; "cleaningtheglass.com"
    ]
    let blogPlatforms = [
        "substack.com"; "medium.com"; "sbnation.com"; "wordpress.com"
    ]

    // Extract site name from URL for display
    let getSiteName (url: string) =
        try
            let uri = Uri(url)
            let host = uri.Host.ToLower().Replace("www.", "")
            match host with
            | h when h.Contains("theathletic") -> "The Athletic"
            | h when h.Contains("espn") -> "ESPN"
            | h when h.Contains("si.com") -> "Sports Illustrated"
            | h when h.Contains("nytimes") -> "NY Times"
            | h when h.Contains("washingtonpost") -> "Washington Post"
            | h when h.Contains("bleacherreport") -> "Bleacher Report"
            | h when h.Contains("cbssports") -> "CBS Sports"
            | h when h.Contains("nbcsports") -> "NBC Sports"
            | h when h.Contains("foxsports") -> "Fox Sports"
            | h when h.Contains("statmuse") -> "StatMuse"
            | h when h.Contains("pro-football-reference") -> "Pro Football Reference"
            | h when h.Contains("basketball-reference") -> "Basketball Reference"
            | h when h.Contains("hockey-reference") -> "Hockey Reference"
            | h when h.Contains("baseball-reference") -> "Baseball Reference"
            | h when h.Contains("fangraphs") -> "FanGraphs"
            | h when h.Contains("footballoutsiders") -> "Football Outsiders"
            | h when h.Contains("pff.com") -> "PFF"
            | h when h.Contains("cleaningtheglass") -> "Cleaning the Glass"
            | h when h.Contains("substack") -> "Substack"
            | h when h.Contains("medium") -> "Medium"
            | h when h.Contains("sbnation") -> "SB Nation"
            | h when h.Contains("yahoo") -> "Yahoo Sports"
            | h when h.Contains("nba.com") -> "NBA.com"
            | h when h.Contains("nfl.com") -> "NFL.com"
            | h when h.Contains("nhl.com") -> "NHL.com"
            | h when h.Contains("mlb.com") -> "MLB.com"
            | _ -> ""  // Return empty for unknown sites - we'll just use the article title
        with _ -> ""

    // Major events (championships, playoffs) should always surface first
    let majorEventKeywords = [
        "super bowl"; "nba finals"; "stanley cup"; "world series"
        "conference final"; "conference championship"; "playoff"
        "nfc championship"; "afc championship"; "eastern conference"
        "western conference"; "division series"; "wild card"
    ]

    // Extract a readable title from URL path as fallback
    let getTitleFromUrl (url: string) =
        try
            let uri = Uri(url)
            let path = uri.AbsolutePath.TrimEnd('/')
            let lastSegment = path.Split('/') |> Array.last
            // Clean up the segment: remove extensions, replace dashes/underscores with spaces
            lastSegment
                .Replace(".html", "").Replace(".htm", "").Replace(".php", "")
                .Replace("-", " ").Replace("_", " ")
                .Split(' ')
                |> Array.map (fun w -> if w.Length > 0 then w.[0].ToString().ToUpper() + w.Substring(1) else w)
                |> String.concat " "
        with _ -> ""

    let groundingUrls =
        candidate
        |> Option.bind (fun c -> c.groundingMetadata)
        |> Option.bind (fun gm -> gm.groundingChunks)
        |> Option.defaultValue []
        |> List.choose (fun chunk ->
            chunk.web
            |> Option.bind (fun web ->
                let urlLower = web.uri.ToLower()
                let rawTitle = if isNull web.title then "" else web.title.Trim()
                // Try to get a good title: prefer web.title, fallback to URL path
                let articleTitle =
                    if String.IsNullOrEmpty(rawTitle) || rawTitle.Length < 10 then
                        let urlTitle = getTitleFromUrl web.uri
                        if urlTitle.Length >= 10 then urlTitle else rawTitle
                    else rawTitle
                // Exclude Wikipedia and other unwanted sources
                if excludedDomains |> List.exists (fun d -> urlLower.Contains(d)) then
                    None
                // Skip links with no usable title
                elif String.IsNullOrEmpty(articleTitle) || articleTitle.Length < 5 then
                    None
                else
                    // Determine link type based on source
                    let linkType =
                        if blogPlatforms |> List.exists (fun d -> urlLower.Contains(d)) then "blog"
                        else "news"
                    // Format title as "Site Name - Article Title" or just article title if unknown site
                    let siteName = getSiteName web.uri
                    let formattedTitle =
                        if String.IsNullOrEmpty(siteName) then articleTitle
                        else sprintf "%s - %s" siteName articleTitle
                    Some { Title = formattedTitle; Url = web.uri; Type = linkType }))
        // Sort: prioritize major events first, then by source quality
        |> List.sortByDescending (fun link ->
            let titleLower = link.Title.ToLower()
            let urlLower = link.Url.ToLower()
            let combinedText = titleLower + " " + urlLower
            // Score: major events get highest priority, then source quality
            let isMajorEvent = majorEventKeywords |> List.exists (fun kw -> combinedText.Contains(kw))
            let sourceScore =
                if priorityPublications |> List.exists (fun d -> urlLower.Contains(d)) then 3
                elif statSites |> List.exists (fun d -> urlLower.Contains(d)) then 2
                elif blogPlatforms |> List.exists (fun d -> urlLower.Contains(d)) then 1
                else 0
            // Major events get +10 to always be first
            (if isMajorEvent then 10 else 0) + sourceScore)

    // Log grounding URL extraction results
    printfn "Extracted %d grounding URLs from Google Search" groundingUrls.Length

    // Clean up the response (remove markdown code blocks if present)
    let cleanedResponse =
        responseText
            .Replace("```json", "")
            .Replace("```", "")
            .Trim()

    // Parse and return the sports narratives response
    try
        let narratives = JsonSerializer.Deserialize<SportsNarrativesResponse> cleanedResponse

        // Filter out Wikipedia and bad links from AI-generated links too
        let filterBadLinks (links: LinkReference list) =
            links
            |> List.filter (fun link ->
                let urlLower = link.Url.ToLower()
                not (excludedDomains |> List.exists (fun d -> urlLower.Contains(d))))

        // Replace AI-generated links with actual grounding URLs
        // Distribute links across narratives so each gets different ones
        let narrativesWithRealLinks =
            if groundingUrls.IsEmpty then
                // Still filter out bad links from AI-generated ones
                let filteredNarratives =
                    narratives.Narratives
                    |> List.map (fun n -> { n with Links = filterBadLinks n.Links })
                { narratives with Narratives = filteredNarratives }
            else
                let mutable usedLinkIndices = Set.empty<int>
                let updatedNarratives =
                    narratives.Narratives
                    |> List.mapi (fun narrativeIndex narrative ->
                        // Extract keywords from narrative title and summary for matching
                        let narrativeText = (narrative.Title + " " + narrative.Summary + " " + narrative.League).ToLower()
                        let keywords =
                            narrativeText.Split([|' '; ','; '.'; ':'; '-'; '\''; '"'|], StringSplitOptions.RemoveEmptyEntries)
                            |> Array.filter (fun w -> w.Length > 3)
                            |> Array.distinct

                        // Find links that match this narrative's keywords
                        let matchingLinks =
                            groundingUrls
                            |> List.indexed
                            |> List.filter (fun (idx, link) ->
                                let linkText = (link.Title + " " + link.Url).ToLower()
                                keywords |> Array.exists (fun kw -> linkText.Contains(kw)))
                            |> List.filter (fun (idx, _) -> not (usedLinkIndices.Contains(idx)))
                            |> List.truncate 2

                        // Mark these links as used
                        matchingLinks |> List.iter (fun (idx, _) -> usedLinkIndices <- usedLinkIndices.Add(idx))

                        // If no matching links, take unused links by position
                        let finalLinks =
                            if matchingLinks.IsEmpty then
                                let startIdx = narrativeIndex * 2
                                groundingUrls
                                |> List.indexed
                                |> List.filter (fun (idx, _) -> not (usedLinkIndices.Contains(idx)))
                                |> List.skip (min startIdx (max 0 (groundingUrls.Length - 2)))
                                |> List.truncate 2
                                |> List.map snd
                            else
                                matchingLinks |> List.map snd

                        { narrative with Links = finalLinks })
                { narratives with Narratives = updatedNarratives }

        return narrativesWithRealLinks
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
