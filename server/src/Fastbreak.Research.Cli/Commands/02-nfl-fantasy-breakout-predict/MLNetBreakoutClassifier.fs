module Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict.MLNetBreakoutClassifier

open System
open System.IO
open Microsoft.ML
open Microsoft.ML.Data
open Microsoft.ML.Trainers
open Microsoft.ML.Transforms
open Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict.FantasyBreakoutModels

type PlayerPredictionOutput = {
    Player: string
    Position: string
    Team: string
    Week: int
    MLConfidence: float32
    SleeperScore: float32
    mutable SleeperHit: bool
    mutable MLHit: bool
    FpDelta: float32
    ActualBreakout: bool  // Keep internally for hit calculation
}

type WeeklyCsvOutput = {
    Week: int
    FilePath: string
    Players: PlayerPredictionOutput[]
}

let private FANTASY_POINT_BREAKOUT_THRESHOLD = 5.0f
let private SLEEPER_SCORE_THRESHOLD = 100.0f

// Load position data from non-raw CSV file
let private loadPositionData (rawFilePath: string) =
    try
        // Convert raw file path to non-raw file path
        let fileName = Path.GetFileName(rawFilePath)
        let dirPath = Path.GetDirectoryName(rawFilePath)
        let nonRawFileName = fileName.Replace("_raw.csv", ".csv")
        let nonRawPath = Path.Combine(dirPath, nonRawFileName)

        if File.Exists(nonRawPath) then
            let lines = File.ReadAllLines(nonRawPath)
            if lines.Length > 1 then
                let header = lines.[0].Split(',')
                let playerIdx = Array.tryFindIndex ((=) "player") header
                let positionIdx = Array.tryFindIndex ((=) "position") header

                match playerIdx, positionIdx with
                | Some pIdx, Some posIdx ->
                    let positionMap = System.Collections.Generic.Dictionary<string, string>()
                    for i in 1 .. lines.Length - 1 do
                        let fields = lines.[i].Split(',')
                        if pIdx < fields.Length && posIdx < fields.Length then
                            let player = fields.[pIdx]
                            let position = fields.[posIdx]
                            if not (positionMap.ContainsKey(player)) then
                                positionMap.Add(player, position)
                    Some positionMap
                | _ -> None
            else None
        else None
    with
    | _ -> None

let private loadRawData (mlContext: MLContext) (filePath: string) (positionMap: System.Collections.Generic.Dictionary<string, string> option) =
    try
        let lines = File.ReadAllLines(filePath)
        if lines.Length <= 1 then
            printfn "Warning: File %s is empty or has no data rows" filePath
            None
        else
            let header = lines.[0].Split(',')
            let playerIdx = Array.tryFindIndex ((=) "player") header
            let positionIdx = Array.tryFindIndex ((=) "position") header
            let teamIdx = Array.tryFindIndex ((=) "team") header
            let fpDeltaIdx = Array.tryFindIndex ((=) "fp_delta") header
            let sleeperScoreIdx = Array.tryFindIndex ((=) "sleeper_score") header

            // Helper function to safely get column index and parse value
            let getColumnValue (columnName: string) (fields: string[]) (defaultVal: float32) =
                match Array.tryFindIndex ((=) columnName) header with
                | Some idx when idx < fields.Length ->
                    match System.Single.TryParse(fields.[idx]) with
                    | true, x -> x
                    | false, _ -> defaultVal
                | _ -> defaultVal

            let getStringValue (columnName: string) (fields: string[]) (defaultVal: string) =
                match Array.tryFindIndex ((=) columnName) header with
                | Some idx when idx < fields.Length -> fields.[idx]
                | _ -> defaultVal

            match playerIdx, fpDeltaIdx, sleeperScoreIdx with
            | Some pIdx, Some fpIdx, Some ssIdx ->
                let data = ResizeArray<FantasyBreakoutInput>()

                for i in 1 .. lines.Length - 1 do
                    let fields = lines.[i].Split(',')
                    if fields.Length > 0 && pIdx < fields.Length && fpIdx < fields.Length && ssIdx < fields.Length then
                        let fpDelta = getColumnValue "fp_delta" fields 0.0f
                        let sleeperScore = getColumnValue "sleeper_score" fields 0.0f

                        let playerName = if pIdx < fields.Length then fields.[pIdx] else ""
                        let position =
                            match positionMap with
                            | Some map when map.ContainsKey(playerName) -> map.[playerName]
                            | _ -> "Unknown"

                        let input = {
                            PlayerId = getStringValue "player_id" fields ""
                            Player = playerName
                            Position = position
                            Team = getStringValue "team" fields "Unknown"
                            DraftNumber = getColumnValue "draft_number" fields 0.0f
                            College = getStringValue "college" fields ""
                            Height = getColumnValue "height" fields 0.0f
                            Weight = getColumnValue "weight" fields 0.0f
                            Age = getColumnValue "age" fields 0.0f
                            EntryYear = getColumnValue "entry_year" fields 0.0f
                            YearsExp = getColumnValue "years_exp" fields 0.0f
                            GamesPlayedY1 = getColumnValue "games_played_y1" fields 0.0f
                            TotalFantasyPointsY1 = getColumnValue "total_fantasy_points_y1" fields 0.0f
                            PpgY1 = getColumnValue "ppg_y1" fields 0.0f
                            TotalOffSnapsY1 = getColumnValue "total_off_snaps_y1" fields 0.0f
                            AvgSnapPctY1 = getColumnValue "avg_snap_pct_y1" fields 0.0f
                            FpPerSnapY1 = getColumnValue "fp_per_snap_y1" fields 0.0f
                            FpPerGameY1 = getColumnValue "fp_per_game_y1" fields 0.0f
                            TotalGamesY2 = getColumnValue "total_games_y2" fields 0.0f
                            AvgSnapPctY2 = getColumnValue "avg_snap_pct_y2" fields 0.0f
                            W1SnapShare = getColumnValue "w1_snap_share" fields 0.0f
                            SnapPctChange = getColumnValue "snap_pct_change" fields 0.0f
                            Y2SnapShareChange = getColumnValue "y2_snap_share_change" fields 0.0f
                            SlidingWindowAvgDelta = getColumnValue "sliding_window_avg_delta" fields 0.0f
                            SnapPctVariance = getColumnValue "snap_pct_variance" fields 0.0f
                            SnapIncreaseMomentum = getColumnValue "snap_increase_momentum" fields 0.0f
                            Crossed10pctSnaps = getColumnValue "crossed_10pct_snaps" fields 0.0f
                            Crossed20pctSnaps = getColumnValue "crossed_20pct_snaps" fields 0.0f
                            Crossed30pctSnaps = getColumnValue "crossed_30pct_snaps" fields 0.0f
                            HasPositiveTrend = getColumnValue "has_positive_trend" fields 0.0f
                            SignificantSnapJump = getColumnValue "significant_snap_jump" fields 0.0f
                            IsUdfa = getColumnValue "is_udfa" fields 0.0f
                            IsDay3Pick = getColumnValue "is_day3_pick" fields 0.0f
                            IsEarlyPick = getColumnValue "is_early_pick" fields 0.0f
                            IsYoungBreakout = getColumnValue "is_young_breakout" fields 0.0f
                            EliteMatchup = getColumnValue "elite_matchup" fields 0.0f
                            GoodMatchup = getColumnValue "good_matchup" fields 0.0f
                            ToughMatchup = getColumnValue "tough_matchup" fields 0.0f
                            RbSizeScore = getColumnValue "rb_size_score" fields 0.0f
                            WrHeightScore = getColumnValue "wr_height_score" fields 0.0f
                            TeSizeScore = getColumnValue "te_size_score" fields 0.0f
                            RookieYearUsage = getColumnValue "rookie_year_usage" fields 0.0f
                            Opponent = getStringValue "opponent" fields ""
                            RushDefenseRank = getColumnValue "rush_defense_rank" fields 0.0f
                            PassDefenseRank = getColumnValue "pass_defense_rank" fields 0.0f
                            RelevantDefRank = getColumnValue "relevant_def_rank" fields 0.0f
                            MatchupScore = getColumnValue "matchup_score" fields 0.0f
                            Ecr = getColumnValue "ecr" fields 0.0f
                            EcrRangeMin = getColumnValue "ecr_range_min" fields 0.0f
                            EcrRangeMax = getColumnValue "ecr_range_max" fields 0.0f
                            DraftValue = getColumnValue "draft_value" fields 0.0f
                            PerformanceScore = getColumnValue "performance_score" fields 0.0f
                            AgeScore = getColumnValue "age_score" fields 0.0f
                            EcrScore = getColumnValue "ecr_score" fields 0.0f
                            SlidingWindowScore = getColumnValue "sliding_window_score" fields 0.0f
                            SleeperScore = sleeperScore
                            PpgThresholdValue = getColumnValue "ppg_threshold_value" fields 0.0f
                            Season = getColumnValue "season" fields 2024.0f
                            PrevWeekFp = getColumnValue "prev_week_fp" fields 0.0f
                            CurrentWeekFp = getColumnValue "current_week_fp" fields 0.0f
                            FpDelta = fpDelta
                            IsBreakout = fpDelta >= FANTASY_POINT_BREAKOUT_THRESHOLD
                        }

                        data.Add(input)

                if data.Count > 0 then
                    Some(mlContext.Data.LoadFromEnumerable(data))
                else
                    printfn "Warning: No valid data found in file %s" filePath
                    None
            | _ ->
                printfn "Error: Required columns not found in %s. Player: %A, FpDelta: %A, SleeperScore: %A"
                    filePath playerIdx fpDeltaIdx sleeperScoreIdx
                None
    with
    | ex ->
        printfn "Error loading file %s: %s" filePath ex.Message
        printfn "Stack trace: %s" ex.StackTrace
        None

let private extractWeekNumber (fileName: string) =
    try
        let parts = fileName.Split('_')
        let weekPart = parts |> Array.tryFind (fun p -> p.StartsWith("w"))
        match weekPart with
        | Some w ->
            let weekStr = w.Substring(1)
            match System.Int32.TryParse(weekStr) with
            | true, weekNum -> weekNum
            | false, _ -> 0
        | None -> 0
    with
    | _ -> 0

let trainMLNetClassifier (rawFilePaths: string[]) (outputDirectory: string) =
    printfn "\n=== TRAINING ML.NET BINARY CLASSIFIER ==="

    let mlContext = MLContext(seed = Nullable(42))

    // Check if we have data files
    if rawFilePaths.Length = 0 then
        printfn "No training data files provided"
        ([||], 0.0, 0.0)
    else
        // Load all data into a single data view
        let allDataRecords = ResizeArray<FantasyBreakoutInput>()

        for i in 0 .. rawFilePaths.Length - 1 do
            let path = rawFilePaths.[i]
            let positionMap = loadPositionData path
            match loadRawData mlContext path positionMap with
            | Some dataView ->
                let dataEnum = mlContext.Data.CreateEnumerable<FantasyBreakoutInput>(dataView, reuseRowObject = false)
                for record in dataEnum do
                    allDataRecords.Add(record)
            | None -> ()

        let combinedData = mlContext.Data.LoadFromEnumerable(allDataRecords)

        // Split data for training and testing
        let dataSplit = mlContext.Data.TrainTestSplit(combinedData, testFraction = 0.2, seed = Nullable(42))

        // Define the feature columns to use
        let featureColumns = [|
            "SleeperScore"; "PrevWeekFp"; "SnapPctChange"; "SlidingWindowAvgDelta"
            "SnapIncreaseMomentum"; "HasPositiveTrend"; "SignificantSnapJump"
            "PerformanceScore"; "AgeScore"; "EcrScore"; "MatchupScore"
            "YearsExp"; "AvgSnapPctY1"; "FpPerSnapY1"; "AvgSnapPctY2"
        |]

        // Create the training pipeline
        let trainer = mlContext.BinaryClassification.Trainers.SdcaLogisticRegression(
            labelColumnName = "Label",
            featureColumnName = "Features",
            l2Regularization = Nullable(0.1f),
            l1Regularization = Nullable(0.01f),
            maximumNumberOfIterations = Nullable(100))

        let pipeline =
            EstimatorChain()
                .Append(mlContext.Transforms.Concatenate("Features", featureColumns))
                .Append(mlContext.Transforms.NormalizeMinMax("Features", "Features"))
                .Append(trainer)

        printfn "Training model..."
        let model = pipeline.Fit(dataSplit.TrainSet)

        // Evaluate the model
        let predictions = model.Transform(dataSplit.TestSet)
        let metrics = mlContext.BinaryClassification.Evaluate(predictions, "Label")

        printfn "\n=== MODEL METRICS ==="
        printfn "Accuracy: %.2f%%" (metrics.Accuracy * 100.0)
        printfn "AUC: %.3f" metrics.AreaUnderRocCurve
        printfn "F1 Score: %.3f" metrics.F1Score
        printfn "Precision: %.2f%%" (metrics.PositivePrecision * 100.0)
        printfn "Recall: %.2f%%" (metrics.PositiveRecall * 100.0)

        // Generate predictions for each week
        let weeklyOutputs = ResizeArray<WeeklyCsvOutput>()

        for i in 0 .. rawFilePaths.Length - 1 do
            let path = rawFilePaths.[i]
            let weekNum = extractWeekNumber (Path.GetFileName(path))
            let positionMap = loadPositionData path

            match loadRawData mlContext path positionMap with
            | Some weekData ->
                let weekPredictions = model.Transform(weekData)
                let predictionEngine = mlContext.Model.CreatePredictionEngine<FantasyBreakoutInput, FantasyBreakoutPrediction>(model)

                // Get the data as enumerable for processing
                let dataEnum = mlContext.Data.CreateEnumerable<FantasyBreakoutInput>(weekData, reuseRowObject = false)
                let players = ResizeArray<PlayerPredictionOutput>()

                for input in dataEnum do
                    let prediction = predictionEngine.Predict(input)
                    let actualBreakout = input.FpDelta >= FANTASY_POINT_BREAKOUT_THRESHOLD

                    players.Add({
                        Player = input.Player
                        Position = input.Position
                        Team = input.Team
                        Week = weekNum
                        MLConfidence = prediction.Probability
                        SleeperScore = input.SleeperScore
                        SleeperHit = false  // Will be calculated after sorting
                        MLHit = false  // Will be calculated after sorting
                        FpDelta = input.FpDelta
                        ActualBreakout = actualBreakout
                    })

                // Sort by sleeper score and mark top 10 hits
                let sortedBySleeper = players.ToArray() |> Array.sortByDescending (fun p -> p.SleeperScore)
                let sleeperTop10 = sortedBySleeper |> Array.take (min 10 sortedBySleeper.Length)

                // Mark sleeper hits (top 10 by sleeper score that actually broke out)
                for player in sortedBySleeper do
                    let isInTop10 = sleeperTop10 |> Array.exists (fun p -> p.Player = player.Player && p.Week = player.Week)
                    player.SleeperHit <- isInTop10 && player.ActualBreakout

                // Sort by ML confidence and mark top 10 hits
                let sortedByML = players.ToArray() |> Array.sortByDescending (fun p -> p.MLConfidence)
                let mlTop10 = sortedByML |> Array.take (min 10 sortedByML.Length)

                // Mark ML hits (top 10 by ML confidence that actually broke out)
                for player in sortedByML do
                    let isInTop10 = mlTop10 |> Array.exists (fun p -> p.Player = player.Player && p.Week = player.Week)
                    player.MLHit <- isInTop10 && player.ActualBreakout

                let csvPath = Path.Combine(outputDirectory, sprintf "week_%d_predictions.csv" weekNum)
                weeklyOutputs.Add({
                    Week = weekNum
                    FilePath = csvPath
                    Players = players.ToArray()
                })

                printfn "Processed Week %d: %d players" weekNum players.Count
            | None -> ()

        (weeklyOutputs.ToArray(), metrics.Accuracy, metrics.F1Score)

let generateWeeklyCsvFiles (outputs: WeeklyCsvOutput[]) =
    printfn "\n=== GENERATING WEEKLY CSV FILES ==="

    for output in outputs do
        try
            use writer = new StreamWriter(output.FilePath)

            // Write header
            writer.WriteLine("player,position,team,ml_confidence,sleeper_score,sleeper_hit,ml_hit,fp_delta")

            // Sort by ML confidence descending
            let sortedPlayers = output.Players |> Array.sortByDescending (fun p -> p.MLConfidence)

            for player in sortedPlayers do
                writer.WriteLine(
                    sprintf "%s,%s,%s,%.4f,%.2f,%b,%b,%.2f"
                        player.Player
                        player.Position
                        player.Team
                        player.MLConfidence
                        player.SleeperScore
                        player.SleeperHit
                        player.MLHit
                        player.FpDelta
                )

            printfn "Week %d CSV written: %s" output.Week output.FilePath
        with
        | ex -> printfn "Error writing CSV for week %d: %s" output.Week ex.Message

let generateSummaryReport (outputs: WeeklyCsvOutput[]) (outputPath: string) =
    printfn "\n=== GENERATING SUMMARY REPORT ==="

    use writer = new StreamWriter(outputPath)
    writer.WriteLine("week,total_players,ml_top10_hits,sleeper_top10_hits,ml_top3_hits,sleeper_top3_hits,ml_precision_top10,sleeper_precision_top10")

    for output in outputs do
        let sortedByML = output.Players |> Array.sortByDescending (fun p -> p.MLConfidence)
        let sortedBySleeper = output.Players |> Array.sortByDescending (fun p -> p.SleeperScore)

        let mlTop10 = sortedByML |> Array.take (min 10 sortedByML.Length)
        let mlTop3 = sortedByML |> Array.take (min 3 sortedByML.Length)
        let sleeperTop10 = sortedBySleeper |> Array.take (min 10 sortedBySleeper.Length)
        let sleeperTop3 = sortedBySleeper |> Array.take (min 3 sortedBySleeper.Length)

        let mlTop10Hits = mlTop10 |> Array.filter (fun p -> p.ActualBreakout) |> Array.length
        let mlTop3Hits = mlTop3 |> Array.filter (fun p -> p.ActualBreakout) |> Array.length
        let sleeperTop10Hits = sleeperTop10 |> Array.filter (fun p -> p.ActualBreakout) |> Array.length
        let sleeperTop3Hits = sleeperTop3 |> Array.filter (fun p -> p.ActualBreakout) |> Array.length

        let mlPrecisionTop10 = if mlTop10.Length > 0 then float mlTop10Hits / float mlTop10.Length else 0.0
        let sleeperPrecisionTop10 = if sleeperTop10.Length > 0 then float sleeperTop10Hits / float sleeperTop10.Length else 0.0

        writer.WriteLine(
            sprintf "%d,%d,%d,%d,%d,%d,%.3f,%.3f"
                output.Week
                output.Players.Length
                mlTop10Hits
                sleeperTop10Hits
                mlTop3Hits
                sleeperTop3Hits
                mlPrecisionTop10
                sleeperPrecisionTop10
        )

    printfn "Summary report written: %s" outputPath