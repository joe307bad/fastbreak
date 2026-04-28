module Fastbreak.Daily.V2.DataPointEnricher

open System
open System.Collections.Generic
open System.IO
open System.Text.Json
open System.Text.Json.Nodes
open System.Text.Json.Serialization
open Fastbreak.Daily.ChartDownloader
open Fastbreak.Daily.TextSegmentConverter
open Fastbreak.Daily.V2.TopicEnricher

[<CLIMutable>]
type DataPoint = {
    [<JsonPropertyName("subject")>]
    Subject: string
    [<JsonPropertyName("subjectType")>]
    SubjectType: string
    [<JsonPropertyName("name")>]
    Name: string
    [<JsonPropertyName("value")>]
    Value: string
    [<JsonPropertyName("rank"); JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)>]
    Rank: string
    [<JsonPropertyName("source")>]
    Source: string
    [<JsonPropertyName("vizType")>]
    VizType: string
}

[<CLIMutable>]
type EnrichedTopicWithData = {
    [<JsonPropertyName("league")>]
    League: string
    [<JsonPropertyName("category")>]
    Category: string
    [<JsonPropertyName("summary")>]
    Summary: string
    [<JsonPropertyName("summarySegments")>]
    SummarySegments: TextSegment list
    [<JsonPropertyName("teams")>]
    Teams: string list
    [<JsonPropertyName("players")>]
    Players: string list
    [<JsonPropertyName("dataPoints")>]
    DataPoints: DataPoint list
}

// Stat name display rules (consistent across leagues):
//   * Counting stats abbreviated to <= 3 chars (Pts, Reb, Ast, Stl, Blk, TO, Rtg).
//   * "Per game" -> "<stat> / G" (spaces around the slash).
//   * Percentage stats -> "<stat> %" (space before %).
//   * Off / Def / Net used consistently as the prefix for ratings.
let private statAbbreviations : Map<string, string> =
    Map.ofList [
        // NBA / basketball
        "pointsPerGame", "Pts / G"
        "fieldGoalPct", "FG %"
        "threePtPct", "3P %"
        "freeThrowPct", "FT %"
        "reboundsPerGame", "Reb / G"
        "assistsPerGame", "Ast / G"
        "stealsPerGame", "Stl / G"
        "blocksPerGame", "Blk / G"
        "turnoversPerGame", "TO / G"
        "offensiveRating", "Off Rtg"
        "defensiveRating", "Def Rtg"
        "netRating", "Net Rtg"
        "pace", "Pace"
        "effectiveFgPct", "Eff FG %"
        "trueShootingPct", "TS %"
        "offensiveRebPct", "Off Reb %"
        "turnoverPct", "TO %"
        "ftaRate", "FTA Rate"
        "oppEffectiveFgPct", "Opp Eff FG %"
        "oppFtaRate", "Opp FTA Rate"
        "oppTurnoverPct", "Opp TO %"
        "oppOffensiveRebPct", "Opp Off Reb %"
        "turnoverDiff", "TO Dif"
        "record", "Rec"
        // NHL / hockey
        "goalsPerGame", "GF / G"
        "goalsAgainstPerGame", "GA / G"
        "goalDiffPerGame", "Goal Dif"
        "goalDiff", "Goal Dif"
        "shotsForPerGame", "SF / G"
        "shotsAgainstPerGame", "SA / G"
        "powerPlayPct", "PP %"
        "penaltyKillPct", "PK %"
        "xgfPct", "xGF %"
        // MLB / baseball
        "gamesPlayed", "GP"
        "runsPerGame", "R / G"
        "runsAllowedPerGame", "RA / G"
        "battingAvg", "AVG"
        "onBasePct", "OBP"
        "sluggingPct", "SLG"
        "slggingPct", "SLG"
        "ops", "OPS"
        "homeRuns", "HR"
        "homeRunsPerGame", "HR / G"
        "era", "ERA"
        "whip", "WHIP"
        "strikeoutsPerNine", "K / 9"
        "walksPerNine", "BB / 9"
        // NFL / football
        "pointsForPerGame", "PF / G"
        "pointsAgainstPerGame", "PA / G"
        "yardsPerGame", "Yds / G"
        "passingYardsPerGame", "Pass Yds / G"
        "rushingYardsPerGame", "Rush Yds / G"
        "turnoverDifferential", "TO Dif"
        // common
        "wins", "W"
        "losses", "L"
        "winPct", "Win %"
    ]

let private camelToWords (s: string) =
    if String.IsNullOrEmpty s then s
    else
        let buf = System.Text.StringBuilder()
        for i in 0 .. s.Length - 1 do
            let c = s.[i]
            if i > 0 && Char.IsUpper c && not (Char.IsUpper s.[i - 1]) then
                buf.Append(' ') |> ignore
            buf.Append(c) |> ignore
        let words = buf.ToString()
        Char.ToUpper(words.[0]).ToString() + words.Substring(1)

let private prettifyName (name: string) =
    if String.IsNullOrEmpty name then name
    else
        match Map.tryFind name statAbbreviations with
        | Some abbrev -> abbrev
        | None ->
            if name.Contains(' ') || name.Contains('-') || name.Contains('_') then name
            else camelToWords name

let private isRankOnlyStat (name: string) =
    name.EndsWith("Rank", StringComparison.OrdinalIgnoreCase)
    || name.EndsWith("RankDisplay", StringComparison.OrdinalIgnoreCase)

let private isMatchupTrendStat (name: string) =
    name.StartsWith("trend", StringComparison.OrdinalIgnoreCase)
    && name.Length > 5
    && Char.IsUpper(name.[5])

// Strip the "trend" prefix and lowercase the first character so the result
// matches the camelCase keys in `statAbbreviations` (e.g. "trendNetRating" -> "netRating").
let private stripTrendPrefix (name: string) =
    if isMatchupTrendStat name then
        let s = name.Substring(5)
        if s.Length > 0 then Char.ToLower(s.[0]).ToString() + s.Substring(1)
        else s
    else name

let private findMatchupChart (league: string) (charts: ChartData list) =
    charts
    |> List.tryFind (fun c ->
        c.League.Equals(league, StringComparison.OrdinalIgnoreCase)
        && c.Name.ToLowerInvariant().Contains("matchup"))

let private findPlayerChart (league: string) (charts: ChartData list) =
    charts
    |> List.tryFind (fun c ->
        c.League.Equals(league, StringComparison.OrdinalIgnoreCase)
        && c.Name.ToLowerInvariant().Contains("player"))

let private findTrendCharts (league: string) (charts: ChartData list) =
    charts
    |> List.filter (fun c ->
        c.League.Equals(league, StringComparison.OrdinalIgnoreCase)
        && (c.Name.ToLowerInvariant().Contains("trend")
            || (not (isNull c.Title) && c.Title.ToLowerInvariant().Contains("trend"))))

let private valueToString (node: JsonNode) : string =
    if isNull node then ""
    else
        try
            let v = node.AsValue()
            match v.GetValueKind() with
            | JsonValueKind.Number -> v.GetValue<double>() |> string
            | JsonValueKind.String -> v.GetValue<string>()
            | JsonValueKind.True -> "true"
            | JsonValueKind.False -> "false"
            | _ -> v.ToJsonString()
        with _ -> node.ToString()

let private isScalar (n: JsonNode) =
    not (isNull n)
    && n.GetValueKind() <> JsonValueKind.Object
    && n.GetValueKind() <> JsonValueKind.Array
    && n.GetValueKind() <> JsonValueKind.Null

// Extract a rank string from a node that holds {value, rank, rankDisplay}.
// Prefers rankDisplay (e.g. "T6") then falls back to rank.
let private rankOf (wrapper: JsonNode) : string =
    if isNull wrapper || wrapper.GetValueKind() <> JsonValueKind.Object then null
    else
        let rd = wrapper.["rankDisplay"]
        if not (isNull rd) && isScalar rd then valueToString rd
        else
            let r = wrapper.["rank"]
            if not (isNull r) && isScalar r then valueToString r
            else null

// Inspect a matchup chart's first data point to discover the trend window key
// (e.g. "monthTrend") used by trend-prefixed ranking stats. Returns the readable
// form with the trailing "Trend" stripped ("Month") so callers render
// "Month: <stat>" rather than "Month Trend: <stat>".
let private detectTrendLabel (json: JsonNode) : string option =
    try
        let dp = json.["dataPoints"]
        if isNull dp || dp.GetValueKind() <> JsonValueKind.Array then None
        else
            let arr = dp.AsArray() |> Seq.toList
            if List.isEmpty arr then None
            else
                let first = arr.[0]
                if isNull first then None
                else
                    let team = first.["homeTeam"]
                    if isNull team || team.GetValueKind() <> JsonValueKind.Object then None
                    else
                        let stats = team.["stats"]
                        if isNull stats || stats.GetValueKind() <> JsonValueKind.Object then None
                        else
                            stats.AsObject()
                            |> Seq.tryPick (fun kv ->
                                let key = kv.Key
                                if key.EndsWith("Trend", StringComparison.OrdinalIgnoreCase)
                                   && key.Length > 5 then
                                    // "monthTrend" -> "month" -> "Month"
                                    let prefix = key.Substring(0, key.Length - 5)
                                    Some (camelToWords prefix)
                                else None)
    with _ -> None

let private buildStatName (trendLabel: string) (rawStatName: string) =
    if isMatchupTrendStat rawStatName then
        let core = prettifyName (stripTrendPrefix rawStatName)
        sprintf "%s: %s" trendLabel core
    else
        prettifyName rawStatName

let private extractAllTeamStatsFromMatchup (chart: ChartData) (teamAbbrev: string) : DataPoint list =
    try
        let json = JsonNode.Parse(chart.RawJson)
        let trendLabel = detectTrendLabel json |> Option.defaultValue "Trend"
        let result = ResizeArray<DataPoint>()
        let push name value rank =
            result.Add(
                { Subject = teamAbbrev
                  SubjectType = "team"
                  Name = name
                  Value = value
                  Rank = rank
                  Source = chart.Name
                  VizType = chart.VizType })

        // Pattern 1 (NBA / NHL): "rankings" object of stat -> array of {team, value, rank, rankDisplay}
        let rankings = json.["rankings"]
        let hasRankings =
            not (isNull rankings) && rankings.GetValueKind() = JsonValueKind.Object
        if hasRankings then
            for prop in rankings.AsObject() do
                let statName = prop.Key
                let arr = prop.Value
                if not (isRankOnlyStat statName)
                   && not (isNull arr)
                   && arr.GetValueKind() = JsonValueKind.Array then
                    for item in arr.AsArray() do
                        if not (isNull item) then
                            let teamNode = item.["team"]
                            let valueNode = item.["value"]
                            if not (isNull teamNode) && isScalar valueNode then
                                let team = teamNode.GetValue<string>()
                                if team.Equals(teamAbbrev, StringComparison.OrdinalIgnoreCase) then
                                    let prettyName = buildStatName trendLabel statName
                                    push prettyName (valueToString valueNode) (rankOf item)

        // Pattern 2 (MLB): "dataPoints" array of matchups, each with homeTeam/awayTeam {abbreviation, stats}.
        // Only used when the chart has no "rankings" (NBA/NHL provide rankings as the canonical
        // per-team source; their dataPoints carry per-matchup snapshots that include misleading
        // aggregates like cumulative gamesPlayed).
        let dp = json.["dataPoints"]
        if not hasRankings && not (isNull dp) && dp.GetValueKind() = JsonValueKind.Array then
            for matchup in dp.AsArray() do
                if not (isNull matchup) && matchup.GetValueKind() = JsonValueKind.Object then
                    for sideKey in [ "homeTeam"; "awayTeam" ] do
                        let team = matchup.[sideKey]
                        if not (isNull team) && team.GetValueKind() = JsonValueKind.Object then
                            let abbrevNode = team.["abbreviation"]
                            if not (isNull abbrevNode) then
                                let abbrev = abbrevNode.GetValue<string>()
                                if abbrev.Equals(teamAbbrev, StringComparison.OrdinalIgnoreCase) then
                                    let stats = team.["stats"]
                                    if not (isNull stats) && stats.GetValueKind() = JsonValueKind.Object then
                                        for statProp in stats.AsObject() do
                                            let statName = statProp.Key
                                            let statVal = statProp.Value
                                            if not (isRankOnlyStat statName) && not (isNull statVal) then
                                                let inner =
                                                    if statVal.GetValueKind() = JsonValueKind.Object then
                                                        let v = statVal.["value"]
                                                        if isNull v then statVal else v
                                                    else
                                                        statVal
                                                if isScalar inner then
                                                    let prettyName = buildStatName trendLabel statName
                                                    let rank =
                                                        if statVal.GetValueKind() = JsonValueKind.Object then rankOf statVal
                                                        else null
                                                    push prettyName (valueToString inner) rank

        result
        |> List.ofSeq
        |> List.distinctBy (fun d -> d.Subject, d.Name)
    with ex ->
        printfn "    [DataPointEnricher] team extract failed for %s: %s" teamAbbrev ex.Message
        []

let private extractTrendStatForTeam (chart: ChartData) (teamAbbrev: string) : DataPoint option =
    try
        let json = JsonNode.Parse(chart.RawJson)
        let titleNode = json.["title"]
        let title =
            if isNull titleNode then chart.Title
            else
                try titleNode.GetValue<string>() with _ -> chart.Title

        let series = json.["series"]
        if isNull series || series.GetValueKind() <> JsonValueKind.Array then None
        else
            series.AsArray()
            |> Seq.tryPick (fun s ->
                if isNull s then None
                else
                    let labelNode = s.["label"]
                    if isNull labelNode then None
                    else
                        let label = labelNode.GetValue<string>()
                        if not (label.Equals(teamAbbrev, StringComparison.OrdinalIgnoreCase)) then None
                        else
                            let dps = s.["dataPoints"]
                            if isNull dps || dps.GetValueKind() <> JsonValueKind.Array then None
                            else
                                let arr = dps.AsArray() |> Seq.toList
                                if List.isEmpty arr then None
                                else
                                    let last = List.last arr
                                    let y = last.["y"]
                                    if isNull y then None
                                    else
                                        Some
                                            { Subject = teamAbbrev
                                              SubjectType = "team"
                                              Name = title
                                              Value = valueToString y
                                              Rank = null
                                              Source = chart.Name
                                              VizType = chart.VizType })
    with ex ->
        printfn "    [DataPointEnricher] trend extract failed for %s: %s" teamAbbrev ex.Message
        None

let private extractAllPlayerStats (chart: ChartData) (playerName: string) : DataPoint list =
    try
        let json = JsonNode.Parse(chart.RawJson)
        let labelOf (key: string) (fallback: string) =
            let n = json.[key]
            if isNull n then fallback
            else try n.GetValue<string>() with _ -> fallback
        let xLabel = labelOf "xColumnLabel" "x"
        let yLabel = labelOf "yColumnLabel" "y"
        let sumLabelOpt =
            let n = json.["sumColumnLabel"]
            if isNull n then None
            else try Some (n.GetValue<string>()) with _ -> None

        let dp = json.["dataPoints"]
        if isNull dp || dp.GetValueKind() <> JsonValueKind.Array then []
        else
            let result = ResizeArray<DataPoint>()
            for item in dp.AsArray() do
                if not (isNull item) then
                    let labelNode = item.["label"]
                    if not (isNull labelNode) then
                        let label = labelNode.GetValue<string>()
                        if label.Equals(playerName, StringComparison.OrdinalIgnoreCase) then
                            let pushIfPresent (key: string) (name: string) =
                                let n = item.[key]
                                if not (isNull n) && isScalar n then
                                    result.Add(
                                        { Subject = playerName
                                          SubjectType = "player"
                                          Name = name
                                          Value = valueToString n
                                          Rank = null
                                          Source = chart.Name
                                          VizType = chart.VizType })
                            pushIfPresent "x" xLabel
                            pushIfPresent "y" yLabel
                            // Only include the "sum" axis when the chart provides a label for it.
                            match sumLabelOpt with
                            | Some sumLabel -> pushIfPresent "sum" sumLabel
                            | None -> ()
            List.ofSeq result
    with ex ->
        printfn "    [DataPointEnricher] player extract failed for %s: %s" playerName ex.Message
        []

// In-memory pipeline stage: download charts and add per-team / per-player data points.
let enrichWithDataPointsCore (topics: EnrichedTopic list) = async {
    printfn "[DataPointEnricher] enriching %d topics" (List.length topics)

    let! charts = downloadCharts ()

    let rng = Random()
    // Tracks (subject, pretty stat name) pairs already used so we never repeat across topics.
    let usedKeys = HashSet<string * string>()

    let pickOneUnused (candidates: DataPoint list) : DataPoint option =
        let unused =
            candidates
            |> List.filter (fun dp -> not (usedKeys.Contains((dp.Subject, dp.Name))))
        if List.isEmpty unused then None
        else
            let pick = unused.[rng.Next(List.length unused)]
            usedKeys.Add((pick.Subject, pick.Name)) |> ignore
            Some pick

    let enriched =
        topics
        |> List.mapi (fun i t ->
            printfn ""
            printfn "[DataPointEnricher] (%d/%d) %s / %s" (i + 1) (List.length topics) t.League t.Category

            let matchupChart = findMatchupChart t.League charts
            let playerChart = findPlayerChart t.League charts
            let trendCharts = findTrendCharts t.League charts

            match matchupChart with
            | Some c -> printfn "    matchup chart: %s" c.Name
            | None -> printfn "    matchup chart: (none for %s)" t.League
            match playerChart with
            | Some c -> printfn "    player chart:  %s" c.Name
            | None -> printfn "    player chart:  (none for %s)" t.League
            if not (List.isEmpty trendCharts) then
                printfn "    trend charts:  %s" (trendCharts |> List.map (fun c -> c.Name) |> String.concat ", ")

            // For each team, gather all candidate stats (matchup + trend), then pick one random unused.
            let teamPoints =
                t.Teams
                |> List.choose (fun team ->
                    let matchupStats =
                        match matchupChart with
                        | None -> []
                        | Some c -> extractAllTeamStatsFromMatchup c team
                    let trendStats =
                        trendCharts |> List.choose (fun c -> extractTrendStatForTeam c team)
                    pickOneUnused (matchupStats @ trendStats))

            // For each player, pick one random unused stat.
            let playerPoints =
                match playerChart with
                | None -> []
                | Some chart ->
                    t.Players
                    |> List.choose (fun player ->
                        pickOneUnused (extractAllPlayerStats chart player))

            let dataPoints = teamPoints @ playerPoints
            printfn "    %d data points (%d team, %d player)"
                (List.length dataPoints) (List.length teamPoints) (List.length playerPoints)

            { League = t.League
              Category = t.Category
              Summary = t.Summary
              SummarySegments = t.SummarySegments
              Teams = t.Teams
              Players = t.Players
              DataPoints = dataPoints })

    return enriched
}

// File-based wrapper for the standalone `enrich-data-points` CLI subcommand.
let enrichWithDataPoints (enrichedJsonPath: string) = async {
    if not (File.Exists enrichedJsonPath) then
        failwithf "enriched topics JSON file not found: %s" enrichedJsonPath

    let inputJson = File.ReadAllText(enrichedJsonPath)
    let readOptions = JsonSerializerOptions()
    readOptions.AllowTrailingCommas <- true
    readOptions.ReadCommentHandling <- JsonCommentHandling.Skip
    let topics =
        JsonSerializer.Deserialize<EnrichedTopic[]>(inputJson, readOptions)
        |> Array.toList

    printfn "[DataPointEnricher] loaded %d topics from %s" (List.length topics) enrichedJsonPath

    let! enriched = enrichWithDataPointsCore topics

    let outputDir = Path.GetDirectoryName(Path.GetFullPath enrichedJsonPath)
    let outputPath = Path.Combine(outputDir, "topics-with-data-points.json")
    let writeOptions = JsonSerializerOptions(WriteIndented = true)
    let json = JsonSerializer.Serialize(enriched, writeOptions)
    File.WriteAllText(outputPath, json)

    printfn ""
    printfn "[DataPointEnricher] wrote %d topics with data points to %s" (List.length enriched) outputPath

    return List.length enriched
}
