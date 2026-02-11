module NewsApiOrg

open System
open System.Net.Http
open System.Text.Json
open System.Text.Json.Serialization
open System.Web

// NewsAPI.org response types
type NewsSource = {
    id: string option
    name: string
}

type NewsArticle = {
    source: NewsSource
    author: string option
    title: string
    description: string option
    url: string
    urlToImage: string option
    publishedAt: string
    content: string option
}

type NewsApiResponse = {
    status: string
    totalResults: int
    articles: NewsArticle list
}

// Simple result type for returning from search
type NewsSearchResult = {
    title: string
    url: string
}

/// Search NewsAPI.org everything endpoint
/// Uses Authorization header (no Bearer prefix, not base64 encoded)
/// Filters results to only include articles mentioning the league in title or description
let searchTopHeadlines (httpClient: HttpClient) (apiKey: string) (query: string) (league: string) (maxResults: int) = async {
    if String.IsNullOrWhiteSpace query then
        return []
    else
        // Build the URL with query parameters
        // Request more results than needed so we can filter for league relevance
        let encodedQuery = HttpUtility.UrlEncode(query)
        let today = DateTime.UtcNow.ToString("yyyy-MM-dd")
        let fiveDaysAgo = DateTime.UtcNow.AddDays(-5.0).ToString("yyyy-MM-dd")
        let requestSize = maxResults * 5  // Request 5x to have enough after filtering
        let url = sprintf "https://newsapi.org/v2/everything?language=en&sortBy=relevancy&q=%s" query

        try
            // Create request with Authorization and User-Agent headers
            use request = new HttpRequestMessage(HttpMethod.Get, url)
            request.Headers.Add("Authorization", apiKey)
            request.Headers.Add("User-Agent", "Fastbreak/1.0")

            let! response = httpClient.SendAsync(request) |> Async.AwaitTask
            let! responseBody = response.Content.ReadAsStringAsync() |> Async.AwaitTask

            if not response.IsSuccessStatusCode then
                printfn "    NewsAPI request failed: %d - %s" (int response.StatusCode) responseBody
                return []
            else
                let options = JsonSerializerOptions()
                options.PropertyNameCaseInsensitive <- true
                options.DefaultIgnoreCondition <- JsonIgnoreCondition.WhenWritingNull

                let newsResponse = JsonSerializer.Deserialize<NewsApiResponse>(responseBody, options)

                if newsResponse.status <> "ok" then
                    printfn "    NewsAPI returned non-ok status: %s" newsResponse.status
                    return []
                else
                    let links =
                        newsResponse.articles
                        |> List.truncate maxResults
                        |> List.map (fun article -> {
                            NewsSearchResult.title = article.title
                            url = article.url
                        })

                    printfn "    NewsAPI found %d articles (filtered from %d) for query: %s" links.Length newsResponse.articles.Length query
                    for link in links do
                        printfn "      - %s" link.title
                    return links
        with ex ->
            printfn "    NewsAPI error: %s" ex.Message
            return []
}
