module Fastbreak.Daily.NarrativeGenerator

open System
open System.Net.Http
open System.Text
open System.Text.Json
open System.Text.Json.Serialization

// Request types (internal, not private - STJ needs access)
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
type GeminiRequest = {
    [<JsonPropertyName("contents")>]
    contents: Content[]
    [<JsonPropertyName("tools")>]
    tools: GoogleSearchTool[]
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

type Candidate = {
    [<JsonPropertyName("content")>]
    Content: ResponseContent
}

type GeminiResponse = {
    [<JsonPropertyName("candidates")>]
    Candidates: Candidate[]
}

// Output types
type NarrativeItem = {
    [<JsonPropertyName("title")>]
    Title: string
    [<JsonPropertyName("analysis")>]
    Analysis: string
}

type NarrativeOutput = {
    [<JsonPropertyName("nfl")>]
    Nfl: NarrativeItem[]
    [<JsonPropertyName("nba")>]
    Nba: NarrativeItem[]
    [<JsonPropertyName("nhl")>]
    Nhl: NarrativeItem[]
    [<JsonPropertyName("mlb")>]
    Mlb: NarrativeItem[]
}

let private buildSystemPrompt () =
    let today = DateTime.Now.ToString("MMMM d, yyyy")
    $"""You are a sports analyst. Today's date is {today}.

For each league (NFL, NBA, NHL, MLB), search the internet, provide 2 paragraphs of analysis for current or future events or offseasons with specific stats. Put as many stats as possible in each analysis.

Format your response as JSON only, no markdown:
{{
    "nfl": [{{ "title": "title 1", "analysis": "analysis 1" }}, {{ "title": "title 2", "analysis": "analysis 2" }}],
    "nba": [{{ "title": "title 1", "analysis": "analysis 1" }}, {{ "title": "title 2", "analysis": "analysis 2" }}],
    "nhl": [{{ "title": "title 1", "analysis": "analysis 1" }}, {{ "title": "title 2", "analysis": "analysis 2" }}],
    "mlb": [{{ "title": "title 1", "analysis": "analysis 1" }}, {{ "title": "title 2", "analysis": "analysis 2" }}]
}}"""

let private getApiKey () =
    Environment.GetEnvironmentVariable("GEMINI_API_KEY")
    |> Option.ofObj
    |> Option.defaultWith (fun () -> failwith "GEMINI_API_KEY environment variable not set")

let private callGemini (apiKey: string) = async {
    use client = new HttpClient()

    let url = $"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={apiKey}"

    let request = {
        contents = [| { parts = [| { text = buildSystemPrompt() } |] } |]
        tools = [| { google_search = obj() } |]
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

let private parseNarratives (text: string) =
    let startIdx = text.IndexOf('{')
    let endIdx = text.LastIndexOf('}')
    if startIdx >= 0 && endIdx > startIdx then
        let jsonPart = text.Substring(startIdx, endIdx - startIdx + 1)
        JsonSerializer.Deserialize<NarrativeOutput>(jsonPart)
    else
        failwithf "Could not find JSON in response: %s" text

let generate () = async {
    printfn "Generating sports narratives with grounded search..."
    let apiKey = getApiKey()
    let! rawText = callGemini apiKey
    let narratives = parseNarratives rawText
    printfn "Done!"
    return narratives
}
