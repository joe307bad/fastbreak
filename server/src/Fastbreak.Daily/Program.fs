open System
open System.Net.Http
open System.Text
open System.Text.Json
open System.Text.Json.Serialization

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

type SourceReference = {
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
    [<JsonPropertyName("dataPoints")>]
    DataPoints: DataPoint list
    [<JsonPropertyName("sources")>]
    Sources: SourceReference list
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

let getSportsNarratives () = async {
    // Get API key from environment
    let apiKey = Environment.GetEnvironmentVariable "GEMINI_API_KEY"

    if String.IsNullOrEmpty apiKey then
        failwith "GEMINI_API_KEY environment variable not set"

    // Fetch all chart data
    printfn "Fetching chart data from CloudFront..."
    let! charts = fetchChartData()

    // Build context from charts
    let chartsContext =
        charts
        |> List.map (fun (name, data) ->
            sprintf "Chart: %s\nData: %s\n" name (data.Substring(0, min 500 data.Length)))
        |> String.concat "\n---\n"

    // Prepare the prompt for Gemini
    let today = DateTime.UtcNow.ToString("yyyy-MM-dd")
    let prompt =
        sprintf """Today's date is %s.

I have the following sports analytics charts available:

%s

Based on these charts, search the web for current sports news, blog posts about advanced analytics, and create 3-5 compelling narratives that use the chart data as evidence.

For each narrative:
1. Find relevant current news or advanced analytics blog posts
2. Connect the news/analysis to insights from the charts
3. Highlight specific data points from the charts
4. Provide URLs to news articles or analytics blogs

Return ONLY a JSON object in this exact format (no markdown, no extra text):
{
  "date": "%s",
  "narratives": [
    {
      "title": "Compelling narrative title",
      "summary": "2-3 sentence summary connecting news to chart insights",
      "chartEvidence": [
        {
          "chartName": "nhl__team_efficiency.json",
          "chartUrl": "https://d2jyizt5xogu23.cloudfront.net/dev/nhl__team_efficiency.json",
          "relevance": "Why this chart supports the narrative"
        }
      ],
      "dataPoints": [
        {
          "metric": "Metric name from chart",
          "value": "Specific value or trend",
          "context": "Why this matters"
        }
      ],
      "sources": [
        {
          "title": "Article or blog post title",
          "url": "https://...",
          "type": "news" or "blog"
        }
      ]
    }
  ]
}

Focus on:
- NHL, NBA, NFL, MLB, or Soccer depending on available charts
- Advanced analytics insights (expected goals, efficiency metrics, etc.)
- Recent news that the data helps explain or predict
- Blog posts from analytics-focused writers""" today chartsContext today

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
            maxOutputTokens = 4000
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
    let narratives = JsonSerializer.Deserialize<SportsNarrativesResponse> cleanedResponse
    return narratives
}

[<EntryPoint>]
let main argv =
    try
        printfn "Fetching sports narratives using chart data and Gemini with grounded search..."
        printfn ""

        let narratives = getSportsNarratives () |> Async.RunSynchronously

        // Serialize to pretty JSON
        let options = new JsonSerializerOptions()
        options.WriteIndented <- true
        let json = JsonSerializer.Serialize(narratives, options)

        printfn "%s" json
        printfn ""
        printfn "Successfully retrieved %d narratives!" narratives.Narratives.Length

        0 // Success
    with
    | ex ->
        eprintfn "Error: %s" ex.Message
        eprintfn "Stack trace: %s" ex.StackTrace
        1 // Error
