module Fastbreak.Daily.ChartDownloader

open System
open System.Net.Http
open System.Text.Json

type RegistryEntry = {
    interval: string
    updatedAt: string
    title: string
}

type ChartData = {
    Name: string
    League: string
    Title: string
    RawJson: string
}

let private baseUrl = "https://d2jyizt5xogu23.cloudfront.net"

let private extractLeague (chartPath: string) =
    // Pattern: dev/nfl__chart_name.json -> nfl
    // Also handle: dev/nfl-chart-name.json -> nfl
    let fileName = chartPath.Replace("dev/", "").Replace(".json", "")
    // Try double underscore first
    let parts = fileName.Split("__")
    if parts.Length >= 2 then
        parts.[0].ToUpperInvariant()
    else
        // Try single dash or underscore - check if first part is a known league
        let firstPart =
            if fileName.Contains("-") then fileName.Split("-").[0]
            elif fileName.Contains("_") then fileName.Split("_").[0]
            else fileName
        let upper = firstPart.ToUpperInvariant()
        if upper = "NFL" || upper = "NBA" || upper = "NHL" || upper = "MLB" || upper = "MLS" then
            upper
        else
            "UNKNOWN"

let downloadCharts () = async {
    use client = new HttpClient()
    client.Timeout <- TimeSpan.FromMinutes(2.0)

    // Download registry
    printfn "Downloading chart registry..."
    let! registryJson = client.GetStringAsync($"{baseUrl}/registry") |> Async.AwaitTask
    let registry = JsonSerializer.Deserialize<Map<string, RegistryEntry>>(registryJson)

    printfn "Found %d charts in registry" (Map.count registry)

    // Download each chart
    let! charts =
        registry
        |> Map.toArray
        |> Array.filter (fun (path, _) -> not (path.Contains("topics"))) // Skip topics
        |> Array.map (fun (path, entry) -> async {
            try
                let url = $"{baseUrl}/{path}"
                let! json = client.GetStringAsync(url) |> Async.AwaitTask
                let league = extractLeague path
                let name = path.Replace("dev/", "").Replace(".json", "")
                printfn "  Downloaded: %s (%s)" name league
                return Some {
                    Name = name
                    League = league
                    Title = entry.title
                    RawJson = json
                }
            with ex ->
                eprintfn "  Failed to download %s: %s" path ex.Message
                return None
        })
        |> Async.Parallel

    let validCharts = charts |> Array.choose id |> Array.toList
    printfn "Downloaded %d charts successfully" (List.length validCharts)

    // Log charts by league for debugging
    let chartsByLeague = validCharts |> List.groupBy (fun c -> c.League)
    chartsByLeague |> List.iter (fun (league, charts) ->
        printfn "  %s: %d charts" league (List.length charts))

    return validCharts
}

let getChartsForLeague (league: string) (charts: ChartData list) =
    charts |> List.filter (fun c -> c.League.Equals(league, StringComparison.OrdinalIgnoreCase))

let summarizeChartData (chart: ChartData) =
    $"Chart: {chart.Title}\nData:\n{chart.RawJson}"
