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

type WebInfo = {
    [<JsonPropertyName("uri")>]
    Uri: string
    [<JsonPropertyName("title")>]
    Title: string
}

type GroundingChunk = {
    [<JsonPropertyName("web")>]
    Web: WebInfo option
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
type RelevantLink = {
    [<JsonPropertyName("title")>]
    Title: string
    [<JsonPropertyName("url")>]
    Url: string
    [<JsonPropertyName("type")>]
    LinkType: string
}

type DataPoint = {
    [<JsonPropertyName("metric")>]
    Metric: string
    [<JsonPropertyName("value")>]
    Value: string
    [<JsonPropertyName("chartName")>]
    ChartName: string
    [<JsonPropertyName("team")>]
    Team: string
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

// Grounded search result with links
type GroundedResult = {
    Text: string
    Links: RelevantLink list
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

Topic: {topic}
Analysis: {rawText}

Create a one sentence title that summarizes the summary and keep the summary text.

Respond with JSON only, no markdown:
{{"title": "title", "summary": "the analysis text"}}"""

// Step 3: Extract relevant data points from chart data
let private buildDataPointsPrompt (league: string) (narrativeTitle: string) (narrativeSummary: string) (chartSummaries: string) =
    $"""Given this {league} narrative and chart data, extract 3-5 relevant data points.

Narrative:
Title: {narrativeTitle}
Summary: {narrativeSummary}

IMPORTANT: Look for any team names or player names mentioned in the narrative. Teams are often abbreviated to 3 letters (e.g., NYK, LAL, BOS, CHI, KC, PHI, DET, SEA, NE, etc.). Find data points for these specific teams/players from the charts.

Chart data:
{chartSummaries}

Find specific metrics and values from the charts that are relevant to this narrative. For each data point, include the 3-letter team abbreviation.

Respond with JSON only, no markdown:
{{"dataPoints": [{{"metric": "metric name", "value": "value with units", "chartName": "name of chart this came from", "team": "ABC"}}, ...]}}"""

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

let getApiKey () =
    Environment.GetEnvironmentVariable("GEMINI_API_KEY")
    |> Option.ofObj
    |> Option.defaultWith (fun () -> failwith "GEMINI_API_KEY environment variable not set")

let private extractLinks (candidate: Candidate option) : RelevantLink list =
    candidate
    |> Option.bind (fun c -> c.GroundingMetadata)
    |> Option.bind (fun m -> m.GroundingChunks)
    |> Option.map (fun chunks ->
        chunks
        |> Array.choose (fun chunk ->
            chunk.Web |> Option.map (fun web -> {
                Title = web.Title
                Url = web.Uri
                LinkType = "source"
            }))
        |> Array.toList)
    |> Option.defaultValue []

// Extract page title from HTML content
let private extractTitleFromHtml (html: string) : string option =
    let titleStart = html.IndexOf("<title>", StringComparison.OrdinalIgnoreCase)
    if titleStart >= 0 then
        let contentStart = titleStart + 7
        let titleEnd = html.IndexOf("</title>", contentStart, StringComparison.OrdinalIgnoreCase)
        if titleEnd > contentStart then
            let title = html.Substring(contentStart, titleEnd - contentStart).Trim()
            // Clean up HTML entities
            let cleaned =
                title
                    .Replace("&amp;", "&")
                    .Replace("&quot;", "\"")
                    .Replace("&#39;", "'")
                    .Replace("&lt;", "<")
                    .Replace("&gt;", ">")
                    .Replace("&#x27;", "'")
                    .Replace("&nbsp;", " ")
            if String.IsNullOrWhiteSpace(cleaned) then None else Some cleaned
        else None
    else None

// Fetch page and extract title, returning (title, finalUrl) after following redirects
let private fetchPageTitleAndUrl (url: string) = async {
    try
        // Create a dedicated client for fetching page titles with proper settings
        use handler = new System.Net.Http.HttpClientHandler()
        handler.AllowAutoRedirect <- true
        handler.MaxAutomaticRedirections <- 5
        use titleClient = new HttpClient(handler)
        titleClient.Timeout <- TimeSpan.FromSeconds(10.0)
        titleClient.DefaultRequestHeaders.Add("User-Agent", "Mozilla/5.0 (compatible; FastbreakBot/1.0)")

        use! response = titleClient.GetAsync(url) |> Async.AwaitTask
        let finalUrl = response.RequestMessage.RequestUri.ToString()
        let! html = response.Content.ReadAsStringAsync() |> Async.AwaitTask
        let title = extractTitleFromHtml html
        return (title, Some finalUrl)
    with ex ->
        printfn "      [Title fetch failed for %s: %s]" (url.Substring(0, min 50 url.Length)) ex.Message
        return (None, None)
}

// Check if a title is bad and needs to be refetched
let private isBadTitle (title: string) =
    if String.IsNullOrWhiteSpace(title) then true
    else
        let normalized = title.Trim().ToLowerInvariant()
        // Domain-only titles typically have no spaces and end with a TLD
        let hasNoSpaces = not (normalized.Contains(" "))
        let looksLikeDomain =
            normalized.EndsWith(".com") ||
            normalized.EndsWith(".org") ||
            normalized.EndsWith(".net") ||
            normalized.EndsWith(".tv") ||
            normalized.EndsWith(".io") ||
            normalized.EndsWith(".co") ||
            normalized.EndsWith(".edu") ||
            normalized.EndsWith(".gov") ||
            normalized.EndsWith(".ca")
        let isDomainOnly = hasNoSpaces && looksLikeDomain
        // Also check for generic/useless titles
        let isGenericTitle =
            normalized.Contains("share on") ||
            normalized.Contains("vertexaisearch") ||
            normalized.Contains("grounding-api") ||
            normalized = "home" ||
            normalized = "news" ||
            normalized = "sports" ||
            normalized.StartsWith("http")
        isDomainOnly || isGenericTitle

// Extract site name from URL
let private extractSiteName (url: string) =
    try
        let uri = Uri(url)
        let host = uri.Host.ToLowerInvariant()
        // Remove www. prefix and get the domain name
        let domain = if host.StartsWith("www.") then host.Substring(4) else host
        // Capitalize first letter of each part
        domain.Split('.')
        |> Array.head
        |> fun s -> if s.Length > 0 then s.Substring(0, 1).ToUpperInvariant() + s.Substring(1) else s
    with _ -> ""

// Add site name to title if not already present
let private addSiteNameToTitle (title: string) (url: string) =
    let siteName = extractSiteName url
    if String.IsNullOrWhiteSpace(siteName) then title
    elif title.ToLowerInvariant().Contains(siteName.ToLowerInvariant()) then title
    else $"{title} | {siteName}"

// Enrich links with fetched page titles and site names
let private enrichLinksWithTitles (links: RelevantLink list) = async {
    let! enrichedLinks =
        links
        |> List.map (fun link -> async {
            // Always fetch to get the final URL after redirects (for proper site name)
            let! (fetchedTitle, finalUrl) = fetchPageTitleAndUrl link.Url
            let urlForSiteName = finalUrl |> Option.defaultValue link.Url

            // Determine the best title to use
            let needsNewTitle = String.IsNullOrWhiteSpace(link.Title) || link.Title.Length < 10 || isBadTitle link.Title
            let bestTitle =
                if needsNewTitle then
                    fetchedTitle |> Option.defaultValue link.Title
                else
                    link.Title

            // Add site name from the final URL (not the redirect URL)
            let titleWithSite = addSiteNameToTitle bestTitle urlForSiteName
            return { link with Title = titleWithSite }
        })
        |> Async.Parallel
    return enrichedLinks |> Array.toList
}

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

    // Extract links from grounding metadata
    let links = extractLinks candidate

    let searchQueries =
        candidate
        |> Option.bind (fun c -> c.GroundingMetadata)
        |> Option.bind (fun m -> m.WebSearchQueries)
        |> Option.defaultValue [||]

    printfn "    [Grounding: %d queries, %d links]" searchQueries.Length links.Length

    let text =
        candidate
        |> Option.map (fun c -> c.Content.Parts |> Array.map (fun p -> p.Text) |> String.concat "")
        |> Option.defaultValue ""

    return { Text = text; Links = links }
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
    let startIdx = text.IndexOf('{')
    let endIdx = text.LastIndexOf('}')
    if startIdx >= 0 && endIdx > startIdx then
        let jsonPart = text.Substring(startIdx, endIdx - startIdx + 1)
        let cleaned = jsonPart.Replace("\\$", "$").Replace("\\'", "'")
        let options = JsonSerializerOptions()
        options.AllowTrailingCommas <- true
        try
            let response = JsonSerializer.Deserialize<DataPointsResponse>(cleaned, options)
            response.DataPoints |> Array.toList
        with _ -> []
    else
        []

// Topics for each league - each becomes a separate narrative with its own grounded search
let private getTopics (league: string) =
    match league with
    | "NFL" -> ["recent game results and standout performances and league wide news"; "trades, free agency, or draft news"]
    | "NBA" -> ["recent game results and standout performances and league wide news"; "standings and playoff race implications"]
    | "NHL" -> ["recent game results and standout performances and league wide news"; "standings and playoff race implications"]
    | "MLB" -> ["recent game results and standout performances and league wide news"; "trades, signings, or roster moves"]
    | _ -> ["recent news"; "upcoming events"]

let private generateNarrative (client: HttpClient) (apiKey: string) (league: string) (topic: string) (chartSummaries: string) = async {
    // Step 1: Get grounded search for this specific topic
    let groundedPrompt = buildTopicPrompt league topic
    let! groundedResult = callGeminiWithSearch client apiKey groundedPrompt
    printfn "    [%s] Got %d chars, %d links" topic groundedResult.Text.Length groundedResult.Links.Length

    // Step 2: Structure into title + summary
    let structurePrompt = buildStructurePrompt league topic groundedResult.Text
    let! structuredResponse = callGemini client apiKey structurePrompt
    let parsed = parseJson structuredResponse

    // Step 3: Extract data points from charts
    let! dataPoints = async {
        if String.IsNullOrWhiteSpace chartSummaries then
            return []
        else
            let dpPrompt = buildDataPointsPrompt league parsed.Title parsed.Summary chartSummaries
            let! dpResponse = callGemini client apiKey dpPrompt
            return parseDataPoints dpResponse
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

    // Step 5: Enrich links with fetched page titles
    let! enrichedLinks = enrichLinksWithTitles groundedResult.Links

    return {
        Title = parsed.Title
        Summary = parsed.Summary
        League = league
        Links = enrichedLinks
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
