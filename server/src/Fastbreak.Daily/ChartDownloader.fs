module Fastbreak.Daily.ChartDownloader

open System
open System.Net.Http
open System.Text.Json
open System.Text.Json.Nodes

type RegistryEntry = {
    interval: string
    updatedAt: string
    title: string
}

// Extract visualizationType from chart JSON
let private extractVizType (chartJson: string) =
    try
        let json = JsonNode.Parse(chartJson)
        let vizType = json.["visualizationType"]
        if isNull vizType then "UNKNOWN" else vizType.GetValue<string>()
    with _ -> "UNKNOWN"

type ChartData = {
    Name: string
    League: string
    Title: string
    VizType: string
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
                // Remove dev/ prefix and .json suffix (dev/nba__foo.json -> nba__foo)
                let name = path.Replace("dev/", "").Replace(".json", "")
                printfn "  Downloaded: %s (%s)" name league
                let vizType = extractVizType json
                return Some {
                    Name = name
                    League = league
                    Title = entry.title
                    VizType = vizType
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
    $"Chart: {chart.Title}\nChart ID: {chart.Name}\nVisualization Type: {chart.VizType}\nData:\n{chart.RawJson}"

// Chart filtering to reduce token count for Gemini API
let private maxContextEntries = 10  // Keep top N entries for league context

// Deep simplification for NBA/CBB matchup data - removes verbose player stats
let private simplifyNBAMatchup (matchup: JsonNode) =
    try
        let simplified = JsonObject()
        // Keep essential fields
        let fieldsToKeep = ["gameId"; "gameDate"; "gameName"; "gameStatus"; "gameCompleted"; "location"; "odds"; "results"]
        for field in fieldsToKeep do
            let value = matchup.[field]
            if not (isNull value) then
                simplified.Add(field, value.DeepClone())

        // Simplify homeTeam - keep core info, remove detailed stats object
        let homeTeam = matchup.["homeTeam"]
        if not (isNull homeTeam) then
            let simpleHome = JsonObject()
            for field in ["id"; "name"; "abbreviation"; "wins"; "losses"; "conferenceRank"; "conference"] do
                let v = homeTeam.[field]
                if not (isNull v) then simpleHome.Add(field, v.DeepClone())
            // Keep stats but it's already a simple object with key stats
            let stats = homeTeam.["stats"]
            if not (isNull stats) then simpleHome.Add("stats", stats.DeepClone())
            simplified.Add("homeTeam", simpleHome)

        // Simplify awayTeam - same as homeTeam
        let awayTeam = matchup.["awayTeam"]
        if not (isNull awayTeam) then
            let simpleAway = JsonObject()
            for field in ["id"; "name"; "abbreviation"; "wins"; "losses"; "conferenceRank"; "conference"] do
                let v = awayTeam.[field]
                if not (isNull v) then simpleAway.Add(field, v.DeepClone())
            let stats = awayTeam.["stats"]
            if not (isNull stats) then simpleAway.Add("stats", stats.DeepClone())
            simplified.Add("awayTeam", simpleAway)

        // Skip: homePlayers, awayPlayers, comparisons, tenthNetRatingByWeek, leagueEfficiencyStats
        simplified :> JsonNode
    with _ -> matchup

// Deep simplification for MATCHUP_V2 data - removes player stats, keeps team stats
let private simplifyMatchupV2 (matchup: JsonNode) =
    try
        let simplified = JsonObject()
        // Keep essential fields
        for field in ["game_datetime"; "odds"; "h2h_record"] do
            let value = matchup.[field]
            if not (isNull value) then
                simplified.Add(field, value.DeepClone())

        // Simplify teams - keep team_stats, remove players
        let teams = matchup.["teams"]
        if not (isNull teams) then
            let simpleTeams = JsonObject()
            let teamsObj = teams.AsObject()
            for kvp in teamsObj do
                let teamData = kvp.Value
                let simpleTeamData = JsonObject()
                let teamStats = teamData.["team_stats"]
                if not (isNull teamStats) then
                    simpleTeamData.Add("team_stats", teamStats.DeepClone())
                // Skip: players
                simpleTeams.Add(kvp.Key, simpleTeamData)
            simplified.Add("teams", simpleTeams)

        // Skip: common_opponents, comparisons
        simplified :> JsonNode
    with _ -> matchup

// Deep simplification for CBB matchup data
let private simplifyCBBMatchup (matchup: JsonNode) =
    try
        let simplified = JsonObject()
        // Keep essential fields
        for field in ["gameId"; "gameDate"; "gameName"; "location"; "odds"] do
            let value = matchup.[field]
            if not (isNull value) then
                simplified.Add(field, value.DeepClone())

        // Simplify homeTeam - keep core info and stats
        let homeTeam = matchup.["homeTeam"]
        if not (isNull homeTeam) then
            let simpleHome = JsonObject()
            for field in ["name"; "abbreviation"; "conference"; "apRank"; "srsRank"; "wins"; "losses"; "stats"] do
                let v = homeTeam.[field]
                if not (isNull v) then simpleHome.Add(field, v.DeepClone())
            simplified.Add("homeTeam", simpleHome)

        // Simplify awayTeam
        let awayTeam = matchup.["awayTeam"]
        if not (isNull awayTeam) then
            let simpleAway = JsonObject()
            for field in ["name"; "abbreviation"; "conference"; "apRank"; "srsRank"; "wins"; "losses"; "stats"] do
                let v = awayTeam.[field]
                if not (isNull v) then simpleAway.Add(field, v.DeepClone())
            simplified.Add("awayTeam", simpleAway)

        // Skip: comparisons
        simplified :> JsonNode
    with _ -> matchup

let private matchesTeam (label: string) (teams: string[]) =
    if Array.isEmpty teams then true
    else
        let upper = label.ToUpperInvariant()
        teams |> Array.exists (fun t -> upper.Contains(t.ToUpperInvariant()))

let private filterDataPoints (json: JsonNode) (field: string) (teams: string[]) (getLabel: JsonNode -> string option) =
    try
        let dp = json.[field]
        if isNull dp then json
        else
            let arr = dp.AsArray()
            let filtered =
                arr
                |> Seq.filter (fun item ->
                    match getLabel item with
                    | Some lbl -> matchesTeam lbl teams
                    | None -> true)
                |> Seq.toArray
            let result =
                if filtered.Length < maxContextEntries then
                    arr |> Seq.take (min maxContextEntries arr.Count) |> Seq.toArray
                else
                    filtered
            let newJson = json.DeepClone()
            newJson.[field] <- JsonArray(result |> Array.map (fun x -> x.DeepClone()))
            newJson
    with _ -> json

let filterChartData (chart: ChartData) (teams: string[]) : ChartData =
    if Array.isEmpty teams then chart
    else
        try
            let json = JsonNode.Parse(chart.RawJson)
            let filtered =
                match chart.VizType.ToUpperInvariant() with
                | "BAR_GRAPH" | "SCATTER_PLOT" | "TABLE" ->
                    filterDataPoints json "dataPoints" teams (fun dp ->
                        let lbl = dp.["label"]
                        let tc = dp.["teamCode"]
                        if not (isNull lbl) then Some (lbl.GetValue<string>())
                        elif not (isNull tc) then Some (tc.GetValue<string>())
                        else None)
                | "LINE_CHART" ->
                    filterDataPoints json "series" teams (fun s ->
                        let lbl = s.["label"]
                        if not (isNull lbl) then Some (lbl.GetValue<string>()) else None)
                | "MATCHUP" ->
                    filterDataPoints json "dataPoints" teams (fun dp ->
                        let home = dp.["homeTeam"]
                        let away = dp.["awayTeam"]
                        let h = if isNull home then "" else home.GetValue<string>()
                        let a = if isNull away then "" else away.GetValue<string>()
                        Some (h + " " + a))
                | "MATCHUP_V2" ->
                    // Filter by map key (e.g., "car-tb") and simplify each matchup
                    try
                        let dp = json.["dataPoints"]
                        if isNull dp then json
                        else
                            let obj = dp.AsObject()
                            let filtered =
                                obj
                                |> Seq.filter (fun kvp ->
                                    kvp.Key.Split('-') |> Array.exists (fun p -> matchesTeam p teams))
                                |> Seq.toArray
                            let result =
                                if filtered.Length < maxContextEntries then
                                    obj |> Seq.take (min maxContextEntries obj.Count) |> Seq.toArray
                                else
                                    filtered
                            let newObj = JsonObject()
                            for kvp in result do
                                // Apply deep simplification to each matchup
                                newObj.Add(kvp.Key, simplifyMatchupV2 kvp.Value)
                            let newJson = json.DeepClone()
                            newJson.["dataPoints"] <- newObj
                            newJson
                    with _ -> json
                | "NBA_MATCHUP" ->
                    // Filter and simplify NBA matchups - remove player data
                    let filteredJson = filterDataPoints json "dataPoints" teams (fun dp ->
                        let home = dp.["homeTeam"]
                        let away = dp.["awayTeam"]
                        let hAbbr =
                            if isNull home then None
                            else
                                let a = home.["abbreviation"]
                                if isNull a then None else Some (a.GetValue<string>())
                        let aAbbr =
                            if isNull away then None
                            else
                                let a = away.["abbreviation"]
                                if isNull a then None else Some (a.GetValue<string>())
                        match hAbbr, aAbbr with
                        | Some h, Some a -> Some (h + " " + a)
                        | Some h, None -> Some h
                        | None, Some a -> Some a
                        | None, None -> None)
                    // Apply deep simplification to each matchup
                    let dataPoints = filteredJson.["dataPoints"]
                    if not (isNull dataPoints) then
                        let arr = dataPoints.AsArray()
                        let simplified = arr |> Seq.map simplifyNBAMatchup |> Seq.toArray
                        filteredJson.["dataPoints"] <- JsonArray(simplified)
                    filteredJson
                | "CBB_MATCHUP" ->
                    // Filter and simplify CBB matchups
                    let filteredJson = filterDataPoints json "dataPoints" teams (fun dp ->
                        let home = dp.["homeTeam"]
                        let away = dp.["awayTeam"]
                        let hAbbr =
                            if isNull home then None
                            else
                                let a = home.["abbreviation"]
                                if isNull a then None else Some (a.GetValue<string>())
                        let aAbbr =
                            if isNull away then None
                            else
                                let a = away.["abbreviation"]
                                if isNull a then None else Some (a.GetValue<string>())
                        match hAbbr, aAbbr with
                        | Some h, Some a -> Some (h + " " + a)
                        | Some h, None -> Some h
                        | None, Some a -> Some a
                        | None, None -> None)
                    // Apply deep simplification to each matchup
                    let dataPoints = filteredJson.["dataPoints"]
                    if not (isNull dataPoints) then
                        let arr = dataPoints.AsArray()
                        let simplified = arr |> Seq.map simplifyCBBMatchup |> Seq.toArray
                        filteredJson.["dataPoints"] <- JsonArray(simplified)
                    filteredJson
                | _ -> json
            { chart with RawJson = filtered.ToJsonString() }
        with ex ->
            printfn "    [Filter] Error filtering %s: %s" chart.Name ex.Message
            chart

let summarizeChartDataForTeams (chart: ChartData) (teams: string[]) =
    let filtered = filterChartData chart teams
    $"Chart: {filtered.Title}\nChart ID: {filtered.Name}\nVisualization Type: {filtered.VizType}\nData:\n{filtered.RawJson}"
