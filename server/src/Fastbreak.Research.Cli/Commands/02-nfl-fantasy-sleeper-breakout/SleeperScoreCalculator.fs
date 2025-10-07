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

        lines
        |> Array.skip 1
        |> Array.map (fun line ->
            let parts = line.Split(',')
            {
                PlayerId = parts.[0]
                Player = parts.[1]
                Position = parts.[2]
                Team = parts.[3]
                Opponent = parts.[4]
                DraftNumber = parseOptionalFloat parts.[5]
                Age = parseInt parts.[9]
                SlidingWindowAvgDelta = parseFloat parts.[23]
                Ecr = parseFloat parts.[49]
                PpgY1 = parseFloat parts.[14]
                AnalysisWeek = parseInt parts.[52]
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

        lines
        |> Array.skip 1
        |> Array.map (fun line ->
            let parts = line.Split(',')
            {
                Team = parts.[1]  // team is in column 1
                RushDefenseRank = parseInt parts.[5]  // rush_defense_rank is in column 5
                PassDefenseRank = parseInt parts.[7]  // pass_defense_rank is in column 7
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
