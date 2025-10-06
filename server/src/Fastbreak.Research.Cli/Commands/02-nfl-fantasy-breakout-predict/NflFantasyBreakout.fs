namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO
open System.Globalization
open Argu

module NflFantasyBreakout =

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

    let loadEnvFile () =
        let envPath = Path.Combine(__SOURCE_DIRECTORY__, ".env")
        if File.Exists(envPath) then
            File.ReadAllLines(envPath)
            |> Array.filter (fun line ->
                not (String.IsNullOrWhiteSpace(line)) &&
                not (line.TrimStart().StartsWith("#")) &&
                line.Contains("="))
            |> Array.iter (fun line ->
                let parts = line.Split('=', 2)
                if parts.Length = 2 then
                    Environment.SetEnvironmentVariable(parts.[0].Trim(), parts.[1].Trim()))

    let verifyData () =
        loadEnvFile()

        let weeklyStatsFolder = Environment.GetEnvironmentVariable("WEEKLY_PLAYER_STATS_DATA_FOLDER")
        let defensiveRankingsFile = Environment.GetEnvironmentVariable("DEFENSIVE_POWER_RANKINGS_FILE")

        printfn "Verifying data sources..."
        printfn ""

        let mutable hasErrors = false

        // Verify weekly player stats folder
        if String.IsNullOrEmpty(weeklyStatsFolder) then
            printfn "❌ WEEKLY_PLAYER_STATS_DATA_FOLDER is not set"
            hasErrors <- true
        elif not (Directory.Exists(weeklyStatsFolder)) then
            printfn "❌ WEEKLY_PLAYER_STATS_DATA_FOLDER directory does not exist: %s" weeklyStatsFolder
            hasErrors <- true
        else
            let csvFiles = Directory.GetFiles(weeklyStatsFolder, "*.csv")
            printfn "✅ WEEKLY_PLAYER_STATS_DATA_FOLDER: %s" weeklyStatsFolder
            printfn "   Found %d CSV file(s)" csvFiles.Length
            if csvFiles.Length > 0 then
                csvFiles |> Array.iter (fun file -> printfn "   - %s" (Path.GetFileName(file)))

        printfn ""

        // Verify defensive power rankings file
        if String.IsNullOrEmpty(defensiveRankingsFile) then
            printfn "❌ DEFENSIVE_POWER_RANKINGS_FILE is not set"
            hasErrors <- true
        elif not (File.Exists(defensiveRankingsFile)) then
            printfn "❌ DEFENSIVE_POWER_RANKINGS_FILE does not exist: %s" defensiveRankingsFile
            hasErrors <- true
        elif not (Path.GetExtension(defensiveRankingsFile).ToLower() = ".csv") then
            printfn "❌ DEFENSIVE_POWER_RANKINGS_FILE is not a CSV file: %s" defensiveRankingsFile
            hasErrors <- true
        else
            printfn "✅ DEFENSIVE_POWER_RANKINGS_FILE: %s" defensiveRankingsFile

        printfn ""
        if hasErrors then
            printfn "Verification completed with errors"
            1
        else
            printfn "✅ All data sources verified successfully"
            0

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

    let calculateSleeperScore (playersFile: string) =
        loadEnvFile()

        let defensiveRankingsFile = Environment.GetEnvironmentVariable("DEFENSIVE_POWER_RANKINGS_FILE")

        if String.IsNullOrEmpty(defensiveRankingsFile) then
            printfn "❌ ERROR: Required environment variable DEFENSIVE_POWER_RANKINGS_FILE is not set"
            printfn ""
            printfn "Run 'dotnet run 02-nfl-fantasy-breakout verify-data' to check your configuration"
            1
        else
            printfn "Loading player data from: %s" playersFile
            let players = parsePlayersCsv playersFile

            printfn "Loading defense rankings from: %s" defensiveRankingsFile
            let defenseRankings = parseDefenseRankingsCsv defensiveRankingsFile
            let defenseMap = defenseRankings |> List.map (fun d -> d.Team, d) |> Map.ofList

            printfn ""
            printfn "Calculating sleeper scores for %d players..." players.Length
            printfn ""

            // Calculate min/max ECR for normalization
            let ecrValues = players |> List.map (fun p -> p.Ecr)
            let minEcr = ecrValues |> List.min
            let maxEcr = ecrValues |> List.max

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

            let calculateEcrScore (ecr: float) =
                if maxEcr = minEcr then 10.0
                else 20.0 - ((ecr - minEcr) / (maxEcr - minEcr)) * 20.0

            let calculateDefenseMatchupScore (position: string) (opponent: string) =
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

            let sleeperScores =
                players
                |> List.map (fun player ->
                    let draftScore = calculateDraftValueScore player.DraftNumber
                    let perfScore = calculatePerformanceScore player.Position player.PpgY1
                    let ageScore = calculateAgeScore player.Age
                    let ecrScore = calculateEcrScore player.Ecr
                    let defenseScore = calculateDefenseMatchupScore player.Position player.Opponent
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

            printfn "%-30s %-4s %-4s %-4s %5s %5s %5s %5s %5s %5s %6s"
                "Player" "Pos" "Team" "Opp" "Draft" "Perf" "Age" "ECR" "Def" "Snap" "Total"
            printfn "%s" (String.replicate 100 "-")

            sleeperScores
            |> List.iter (fun score ->
                printfn "%-30s %-4s %-4s %-4s %5.0f %5.0f %5.0f %5.1f %5.0f %5.0f %6.1f"
                    score.Player
                    score.Position
                    score.Team
                    score.Opponent
                    score.DraftValueScore
                    score.PerformanceScore
                    score.AgeScore
                    score.EcrScore
                    score.DefenseMatchupScore
                    score.SnapTrendScore
                    score.TotalScore)

            printfn ""
            printfn "✅ Calculated sleeper scores for %d players" sleeperScores.Length
            0

    let runNflFantasyBreakout (args: ParseResults<'T>) =
        let subcommands = args.GetAllResults()

        if subcommands.IsEmpty then
            printfn "ERROR: A subcommand is required"
            printfn ""
            printfn "Available subcommands:"
            printfn "  verify-data                      Verify data sources are accessible"
            printfn "  calculate-sleeper-score --players <csv_path>  Calculate sleeper scores for fantasy players"
            1
        else
            // Check which subcommand was used
            let hasVerifyData = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Verify_Data"))
            let hasCalculateScore = subcommands |> List.exists (fun cmd -> cmd.ToString().Contains("Calculate_Sleeper_Score"))

            if hasVerifyData then
                verifyData()
            elif hasCalculateScore then
                // Extract the players file path from command line args
                let argStrings = Environment.GetCommandLineArgs()
                let playersIndex = Array.tryFindIndex (fun s -> s = "--players") argStrings
                match playersIndex with
                | Some i when i + 1 < argStrings.Length ->
                    let playersFile = argStrings.[i + 1]
                    calculateSleeperScore playersFile
                | _ ->
                    printfn "ERROR: --players argument is required for calculate-sleeper-score"
                    printfn "Usage: dotnet run 02-nfl-fantasy-breakout calculate-sleeper-score --players <csv_path>"
                    1
            else
                printfn "ERROR: Unknown subcommand"
                1
