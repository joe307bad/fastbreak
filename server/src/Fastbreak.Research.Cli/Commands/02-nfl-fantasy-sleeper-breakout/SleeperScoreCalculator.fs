namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO
open System.Globalization

module SleeperScoreCalculator =

    type PlayerData = {
        PlayerId: string
        Player: string
        Position: string
        Team: string
        Opponent: string
        DraftNumber: float option
        Age: int
        SlidingWindowAvgDelta: float
        Ecr: float
        PpgY1: float
        AnalysisWeek: int
        FantPtsLastWeek: float option
        FantPtsThisWeek: float option
    }

    type DefenseRanking = {
        Team: string
        RushDefenseRank: int
        PassDefenseRank: int
    }

    type SleeperScore = {
        Player: string
        Position: string
        Team: string
        Opponent: string
        DraftValueScore: float
        PerformanceScore: float
        AgeScore: float
        EcrScore: float
        DefenseMatchupScore: float
        SnapTrendScore: float
        TotalScore: float
    }

    type SleeperHit = {
        Player: string
        Position: string
        Team: string
        Opponent: string
        DraftValueScore: float
        PerformanceScore: float
        AgeScore: float
        EcrScore: float
        DefenseMatchupScore: float
        SnapTrendScore: float
        TotalScore: float
        FantPtsLastWeek: float
        FantPtsThisWeek: float
        FantPtsDelta: float
        IsHit: bool
    }

    let parsePlayersCsv (filePath: string) : PlayerData list =
        if not (File.Exists(filePath)) then
            failwithf "Player CSV file not found: %s" filePath

        let lines = File.ReadAllLines(filePath)
        if lines.Length < 2 then
            failwith "CSV file is empty or has no data rows"

        let parseFloat (s: string) =
            match Double.TryParse(s, NumberStyles.Any, CultureInfo.InvariantCulture) with
            | true, v -> v
            | false, _ -> 0.0

        let parseOptionalFloat (s: string) =
            if String.IsNullOrWhiteSpace(s) || s = "NA" then None
            else
                match Double.TryParse(s, NumberStyles.Any, CultureInfo.InvariantCulture) with
                | true, v -> Some v
                | false, _ -> None

        let parseInt (s: string) =
            match Int32.TryParse(s) with
            | true, v -> v
            | false, _ -> 0

        // Parse header to find column indices
        let header = lines.[0].Split(',')
        let findColumnIndex (columnName: string) =
            header |> Array.tryFindIndex (fun h -> h = columnName)

        let playerIdIdx = findColumnIndex "player_id" |> Option.defaultValue 0
        let playerIdx = findColumnIndex "player" |> Option.defaultValue 1
        let positionIdx = findColumnIndex "position" |> Option.defaultValue 2
        let teamIdx = findColumnIndex "team" |> Option.defaultValue 3
        let opponentIdx = findColumnIndex "opponent" |> Option.defaultValue 4
        let draftNumberIdx = findColumnIndex "draft_number" |> Option.defaultValue 5
        let ageIdx = findColumnIndex "age" |> Option.defaultValue 9
        let slidingWindowAvgDeltaIdx = findColumnIndex "sliding_window_avg_delta" |> Option.defaultValue 23
        let ecrIdx = findColumnIndex "ecr" |> Option.defaultValue 52
        let ppgY1Idx = findColumnIndex "ppg_y1" |> Option.defaultValue 14
        let analysisWeekIdx = findColumnIndex "analysis_week" |> Option.defaultValue 55
        let prevWeekFpIdx = findColumnIndex "prev_week_fp"
        let currentWeekFpIdx = findColumnIndex "current_week_fp"

        lines
        |> Array.skip 1
        |> Array.map (fun line ->
            let parts = line.Split(',')
            {
                PlayerId = parts.[playerIdIdx]
                Player = parts.[playerIdx]
                Position = parts.[positionIdx]
                Team = parts.[teamIdx]
                Opponent = parts.[opponentIdx]
                DraftNumber = parseOptionalFloat parts.[draftNumberIdx]
                Age = parseInt parts.[ageIdx]
                SlidingWindowAvgDelta = parseFloat parts.[slidingWindowAvgDeltaIdx]
                Ecr = parseFloat parts.[ecrIdx]
                PpgY1 = parseFloat parts.[ppgY1Idx]
                AnalysisWeek = parseInt parts.[analysisWeekIdx]
                FantPtsLastWeek =
                    match prevWeekFpIdx with
                    | Some idx when parts.Length > idx -> parseOptionalFloat parts.[idx]
                    | _ -> None
                FantPtsThisWeek =
                    match currentWeekFpIdx with
                    | Some idx when parts.Length > idx -> parseOptionalFloat parts.[idx]
                    | _ -> None
            })
        |> Array.toList

    let parseDefenseRankingsCsv (filePath: string) : DefenseRanking list =
        if not (File.Exists(filePath)) then
            failwithf "Defense rankings CSV file not found: %s" filePath

        let lines = File.ReadAllLines(filePath)
        if lines.Length < 2 then
            failwith "Defense rankings CSV is empty or has no data rows"

        let parseInt (s: string) =
            match Int32.TryParse(s) with
            | true, v -> v
            | false, _ -> 99

        // Parse header to find column indices
        let header = lines.[0].Split(',')
        let findColumnIndex (columnName: string) =
            header |> Array.tryFindIndex (fun h -> h = columnName)

        let teamIdx = findColumnIndex "team" |> Option.defaultValue 1
        let rushDefenseRankIdx = findColumnIndex "rush_defense_rank" |> Option.defaultValue 5
        let passDefenseRankIdx = findColumnIndex "pass_defense_rank" |> Option.defaultValue 7

        lines
        |> Array.skip 1
        |> Array.map (fun line ->
            let parts = line.Split(',')
            {
                Team = parts.[teamIdx]
                RushDefenseRank = parseInt parts.[rushDefenseRankIdx]
                PassDefenseRank = parseInt parts.[passDefenseRankIdx]
            })
        |> Array.toList

    let calculateDraftValueScore (draftNumber: float option) =
        match draftNumber with
        | None -> 50.0 // UDFA
        | Some dn when dn > 200.0 -> 40.0
        | Some dn when dn > 150.0 -> 30.0
        | Some dn when dn > 100.0 -> 20.0
        | Some dn when dn > 50.0 -> 10.0
        | Some _ -> 0.0

    let calculatePerformanceScore (position: string) (ppg: float) =
        let threshold = if position = "TE" then 4.0 else 6.0
        if ppg < threshold then 30.0
        elif ppg < threshold * 1.5 then 20.0
        else 0.0

    let calculateAgeScore (age: int) =
        if age <= 22 then 20.0
        elif age <= 23 then 15.0
        elif age <= 24 then 10.0
        else 0.0

    let calculateEcrScore (minEcr: float) (maxEcr: float) (ecr: float) =
        if maxEcr = minEcr then 10.0
        else 20.0 - ((ecr - minEcr) / (maxEcr - minEcr)) * 20.0

    let calculateDefenseMatchupScore (defenseMap: Map<string, DefenseRanking>) (position: string) (opponent: string) =
        match Map.tryFind opponent defenseMap with
        | None -> 0.0
        | Some defense ->
            let rank = if position = "RB" then defense.RushDefenseRank else defense.PassDefenseRank
            if rank >= 29 then 30.0
            elif rank >= 25 then 25.0
            elif rank >= 21 then 20.0
            elif rank >= 17 then 15.0
            elif rank >= 13 then 10.0
            elif rank >= 9 then 5.0
            else 0.0

    let calculateSnapTrendScore (delta: float) (week: int) =
        if week < 3 then 0.0
        elif delta >= 10.0 then 15.0
        elif delta >= 5.0 then 12.0
        elif delta >= 2.0 then 8.0
        elif delta > 0.0 then 5.0
        elif delta >= -2.0 then 2.0
        else 0.0

    let calculateSleeperScores (players: PlayerData list) (defenseMap: Map<string, DefenseRanking>) : SleeperScore list =
        // Calculate min/max ECR for normalization
        let ecrValues = players |> List.map (fun p -> p.Ecr)
        let minEcr = ecrValues |> List.min
        let maxEcr = ecrValues |> List.max

        players
        |> List.map (fun player ->
            let draftScore = calculateDraftValueScore player.DraftNumber
            let perfScore = calculatePerformanceScore player.Position player.PpgY1
            let ageScore = calculateAgeScore player.Age
            let ecrScore = calculateEcrScore minEcr maxEcr player.Ecr
            let defenseScore = calculateDefenseMatchupScore defenseMap player.Position player.Opponent
            let snapScore = calculateSnapTrendScore player.SlidingWindowAvgDelta player.AnalysisWeek
            let total = draftScore + perfScore + ageScore + ecrScore + defenseScore + snapScore

            {
                Player = player.Player
                Position = player.Position
                Team = player.Team
                Opponent = player.Opponent
                DraftValueScore = draftScore
                PerformanceScore = perfScore
                AgeScore = ageScore
                EcrScore = ecrScore
                DefenseMatchupScore = defenseScore
                SnapTrendScore = snapScore
                TotalScore = total
            })
        |> List.sortByDescending (fun s -> s.TotalScore)

    let calculateSleeperHits (players: PlayerData list) (defenseMap: Map<string, DefenseRanking>) : SleeperHit list =
        // Calculate min/max ECR for normalization
        let ecrValues = players |> List.map (fun p -> p.Ecr)
        let minEcr = ecrValues |> List.min
        let maxEcr = ecrValues |> List.max

        players
        |> List.map (fun player ->
            let draftScore = calculateDraftValueScore player.DraftNumber
            let perfScore = calculatePerformanceScore player.Position player.PpgY1
            let ageScore = calculateAgeScore player.Age
            let ecrScore = calculateEcrScore minEcr maxEcr player.Ecr
            let defenseScore = calculateDefenseMatchupScore defenseMap player.Position player.Opponent
            let snapScore = calculateSnapTrendScore player.SlidingWindowAvgDelta player.AnalysisWeek
            let total = draftScore + perfScore + ageScore + ecrScore + defenseScore + snapScore

            let lastWeek = player.FantPtsLastWeek |> Option.defaultValue 0.0
            let thisWeek = player.FantPtsThisWeek |> Option.defaultValue 0.0
            let delta = thisWeek - lastWeek
            let isHit = delta > 5.0

            {
                Player = player.Player
                Position = player.Position
                Team = player.Team
                Opponent = player.Opponent
                DraftValueScore = draftScore
                PerformanceScore = perfScore
                AgeScore = ageScore
                EcrScore = ecrScore
                DefenseMatchupScore = defenseScore
                SnapTrendScore = snapScore
                TotalScore = total
                FantPtsLastWeek = lastWeek
                FantPtsThisWeek = thisWeek
                FantPtsDelta = delta
                IsHit = isHit
            })
        |> List.sortByDescending (fun s -> s.TotalScore)
