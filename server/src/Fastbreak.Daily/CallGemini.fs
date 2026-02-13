module Fastbreak.Daily.CallGemini

open System
open System.Net.Http
open System.Text
open System.Text.Json
open System.Text.Json.Serialization

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
    [<JsonPropertyName("domain")>]
    Domain: string
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

// Output types for grounded search
type RelevantLink = {
    [<JsonPropertyName("title")>]
    Title: string
    [<JsonPropertyName("url")>]
    Url: string
    [<JsonPropertyName("type")>]
    LinkType: string
}

type GroundedSearchResult = {
    Text: string
    Links: RelevantLink list
}

let getApiKey () =
    Environment.GetEnvironmentVariable("GEMINI_API_KEY")
    |> Option.ofObj
    |> Option.defaultWith (fun () -> failwith "GEMINI_API_KEY environment variable not set")

// Capitalize domain name nicely (e.g., "espn.com" -> "ESPN", "foxsports.com.au" -> "Fox Sports")
let formatDomainAsTitle (domain: string) =
    if String.IsNullOrWhiteSpace(domain) then ""
    else
        // Remove TLD suffixes
        let baseDomain =
            domain.ToLowerInvariant()
                .Replace(".com", "").Replace(".org", "").Replace(".net", "")
                .Replace(".au", "").Replace(".ca", "").Replace(".co.uk", "")
                .Replace(".tv", "").Replace(".io", "")
        // Handle common abbreviations
        match baseDomain with
        | "espn" -> "ESPN"
        | "nba" -> "NBA"
        | "nfl" -> "NFL"
        | "nhl" -> "NHL"
        | "mlb" -> "MLB"
        | "cbs" | "cbssports" -> "CBS Sports"
        | "foxsports" -> "Fox Sports"
        | "si" -> "Sports Illustrated"
        | "tsn" -> "TSN"
        | "cbc" -> "CBC"
        | "yahoo" | "yahoosports" -> "Yahoo Sports"
        | "bleacherreport" -> "Bleacher Report"
        | "theathletic" -> "The Athletic"
        | s ->
            // Capitalize first letter of each word
            s.Split([|'-'; '_'|])
            |> Array.map (fun word -> if word.Length > 0 then word.[0].ToString().ToUpperInvariant() + word.Substring(1) else "")
            |> String.concat " "

let private extractLinks (candidate: Candidate option) : RelevantLink list =
    candidate
    |> Option.bind (fun c -> c.GroundingMetadata)
    |> Option.bind (fun m -> m.GroundingChunks)
    |> Option.map (fun chunks ->
        chunks
        |> Array.choose (fun chunk ->
            chunk.Web |> Option.map (fun web ->
                // Use domain field for title, fall back to title field, then format domain from URI
                let displayTitle =
                    if not (String.IsNullOrWhiteSpace(web.Domain)) then
                        formatDomainAsTitle web.Domain
                    elif not (String.IsNullOrWhiteSpace(web.Title)) then
                        formatDomainAsTitle web.Title
                    else
                        "News"
                {
                    Title = displayTitle
                    Url = web.Uri
                    LinkType = "source"
                }))
        |> Array.toList)
    |> Option.defaultValue []

/// Call Gemini without grounding (for text processing tasks)
let callGemini (client: HttpClient) (apiKey: string) (prompt: string) (temperature: float) = async {
    let url = $"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={apiKey}"

    let request: GeminiRequest = {
        contents = [| { parts = [| { text = prompt } |] } |]
        generationConfig = { temperature = temperature }
    }

    let jsonOptions = JsonSerializerOptions()
    jsonOptions.PropertyNamingPolicy <- JsonNamingPolicy.CamelCase

    let json = JsonSerializer.Serialize(request, jsonOptions)
    use content = new StringContent(json, Encoding.UTF8, "application/json")

    let! response = client.PostAsync(url, content) |> Async.AwaitTask
    let! responseBody = response.Content.ReadAsStringAsync() |> Async.AwaitTask

    if not response.IsSuccessStatusCode then
        printfn "      [CallGemini] API error: %s" responseBody
        return None
    else
        try
            let geminiResponse = JsonSerializer.Deserialize<GeminiResponse>(responseBody)
            let text =
                geminiResponse.Candidates
                |> Array.tryHead
                |> Option.bind (fun c ->
                    if isNull (box c.Content) || isNull c.Content.Parts then None
                    else Some (c.Content.Parts |> Array.map (fun p -> p.Text) |> String.concat ""))
            return text
        with ex ->
            printfn "      [CallGemini] Parse error: %s" ex.Message
            return None
}

/// Call Gemini without grounding, throwing on error
let callGeminiOrFail (client: HttpClient) (apiKey: string) (prompt: string) (temperature: float) = async {
    let url = $"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={apiKey}"

    let request: GeminiRequest = {
        contents = [| { parts = [| { text = prompt } |] } |]
        generationConfig = { temperature = temperature }
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
        |> Option.bind (fun c ->
            if isNull (box c.Content) || isNull c.Content.Parts then None
            else Some (c.Content.Parts |> Array.map (fun p -> p.Text) |> String.concat ""))
        |> Option.defaultValue ""
}

/// Call Gemini with Google Search grounding
let callGeminiWithSearch (client: HttpClient) (apiKey: string) (prompt: string) = async {
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

    printfn "      [Grounded] Sending request..."

    let! response = client.PostAsync(url, content) |> Async.AwaitTask
    let! responseBody = response.Content.ReadAsStringAsync() |> Async.AwaitTask

    if not response.IsSuccessStatusCode then
        printfn "      [Grounded] API error: %s" responseBody
        failwithf "Gemini API error: %s" responseBody

    let geminiResponse = JsonSerializer.Deserialize<GeminiResponse>(responseBody)

    if isNull geminiResponse.Candidates || geminiResponse.Candidates.Length = 0 then
        printfn "      [Grounded] No candidates in response"
        printfn "      [Grounded] Response body: %s" (responseBody.Substring(0, min 500 responseBody.Length))
        return { Text = ""; Links = [] }
    else
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
            |> Option.bind (fun c ->
                if isNull (box c.Content) then
                    printfn "      [Grounded] Content is null"
                    None
                elif isNull c.Content.Parts then
                    printfn "      [Grounded] Parts is null"
                    None
                else
                    Some (c.Content.Parts |> Array.map (fun p -> if isNull p.Text then "" else p.Text) |> String.concat ""))
            |> Option.defaultValue ""

        return { Text = text; Links = links }
}
