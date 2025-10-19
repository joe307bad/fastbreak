namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO
open Microsoft.ML
open Microsoft.ML.Data
open Plotly.NET
open Plotly.NET.LayoutObjects
open Plotly.NET.TraceObjects
open DataTypes
open DataLoader

module PredictNewWeek =

    /// Prediction output type for ML.NET
    [<CLIMutable>]
    type BinaryPrediction = {
        [<ColumnName("PredictedLabel")>]
        PredictedLabel: bool

        [<ColumnName("Probability")>]
        Probability: float32

        [<ColumnName("Score")>]
        Score: float32
    }

    /// Player prediction result with all data
    type PlayerPrediction = {
        Stats: PlayerWeeklyStats
        PredictedHit: bool
        Probability: float32
    }

    /// Parse a single CSV row for prediction (no hit column required)
    let parseRowForPrediction (headers: string[]) (values: string[]) : PlayerWeeklyStats option =
        try
            let getValue (name: string) =
                let index = headers |> Array.tryFindIndex (fun h -> h = name)
                match index with
                | Some i when i < values.Length -> values.[i]
                | _ -> ""

            let parseFloat (name: string) =
                let value = getValue name
                if String.IsNullOrWhiteSpace(value) || value.ToUpper() = "NA" || value.ToUpper() = "NAN" then 0.0f
                else
                    match System.Single.TryParse(value) with
                    | (true, v) -> v
                    | (false, _) -> 0.0f

            let parseInt (name: string) =
                let value = getValue name
                if String.IsNullOrWhiteSpace(value) || value.ToUpper() = "NA" then 0
                else
                    match System.Int32.TryParse(value) with
                    | (true, v) -> v
                    | (false, _) -> 0

            let parseBool (name: string) =
                let value = getValue name
                if String.IsNullOrWhiteSpace(value) || value.ToUpper() = "NA" then false
                else value = "1" || value.ToUpper() = "TRUE"

            let getString (name: string) =
                let value = getValue name
                if String.IsNullOrWhiteSpace(value) then ""
                else value

            Some {
                PlayerId = getString "player_id"
                Player = getString "player"
                Position = getString "position"
                Team = getString "team"
                Opponent = getString "opponent"
                Hit = false  // Will be predicted
                PrevWeekFp = parseFloat "prev_week_fp"
                CurrentWeekFp = parseFloat "current_week_fp"
                SleeperScore = parseFloat "sleeper_score"
                FpWeekDelta = parseFloat "fp_week_delta"
                TotalFpY2 = parseFloat "total_fp_y2"
                AvgFpY2 = parseFloat "avg_fp_y2"
                MaxFpY2 = parseFloat "max_fp_y2"
                MinFpY2 = parseFloat "min_fp_y2"
                FpPerSnapY2 = parseFloat "fp_per_snap_y2"
                FpConsistencyY2 = parseFloat "fp_consistency_y2"
                TotalFantasyPointsY1 = parseFloat "total_fantasy_points_y1"
                PpgY1 = parseFloat "ppg_y1"
                FpPerSnapY1 = parseFloat "fp_per_snap_y1"
                W1SnapShare = parseFloat "w1_snap_share"
                Y2SnapShareChange = parseFloat "y2_snap_share_change"
                SlidingWindowAvgDelta = parseFloat "sliding_window_avg_delta"
                MaxSnapPctY2 = parseFloat "max_snap_pct_y2"
                MinSnapPctY2 = parseFloat "min_snap_pct_y2"
                AvgSnapPctY2 = parseFloat "avg_snap_pct_y2"
                SnapPctChange = parseFloat "snap_pct_change"
                SnapPctVariance = parseFloat "snap_pct_variance"
                SnapConsistencyY2 = parseFloat "snap_consistency_y2"
                TotalOffSnapsY2 = parseFloat "total_off_snaps_y2"
                TotalOffSnapsY1 = parseFloat "total_off_snaps_y1"
                AvgSnapPctY1 = parseFloat "avg_snap_pct_y1"
                Crossed10PctSnaps = parseBool "crossed_10pct_snaps"
                Crossed20PctSnaps = parseBool "crossed_20pct_snaps"
                Crossed30PctSnaps = parseBool "crossed_30pct_snaps"
                HasPositiveTrend = parseBool "has_positive_trend"
                SignificantSnapJump = parseBool "significant_snap_jump"
                Height = parseFloat "height"
                Weight = parseFloat "weight"
                Age = parseFloat "age"
                RbSizeScore = parseFloat "rb_size_score"
                WrHeightScore = parseFloat "wr_height_score"
                TeSizeScore = parseFloat "te_size_score"
                DraftNumber = parseFloat "draft_number"
                EntryYear = parseInt "entry_year"
                YearsExp = parseInt "years_exp"
                College = getString "college"
                IsUdfa = parseBool "is_udfa"
                IsDay3Pick = parseBool "is_day3_pick"
                IsEarlyPick = parseBool "is_early_pick"
                IsYoungBreakout = parseBool "is_young_breakout"
                GamesY1 = parseFloat "games_y1"
                RookieYearUsage = parseFloat "rookie_year_usage"
                OpponentRushDefRank = parseFloat "opponent_rush_def_rank"
                OpponentPassDefRank = parseFloat "opponent_pass_def_rank"
                Ecr = parseFloat "ecr"
                PlayerAvailable = parseFloat "player_available"
                DraftValueScore = parseFloat "draft_value_score"
                PerformanceScore = parseFloat "performance_score"
                AgeScore = parseFloat "age_score"
                EcrScore = parseFloat "ecr_score"
                MatchupScore = parseFloat "matchup_score"
                SnapTrendScore = parseFloat "snap_trend_score"
                Season = parseInt "season"
                AnalysisWeek = parseInt "analysis_week"
                TotalGamesY2 = parseFloat "total_games_y2"
                GamesPlayedY2 = parseFloat "games_played_y2"
            }
        with ex ->
            printfn "Error parsing row: %s" ex.Message
            None

    /// Train model on all data
    let private trainModel (mlContext: MLContext) (trainingDataFolder: string) =
        printfn "Loading training data from: %s" trainingDataFolder
        let allData = loadAllData trainingDataFolder
        printfn "Loaded %d player-weeks for training" allData.Length
        printfn ""

        let modelInputs = allData |> List.map toModelInput
        let dataView = mlContext.Data.LoadFromEnumerable(modelInputs)

        printfn "Training LbfgsLogisticRegression model..."

        let featureColumns =
            [|
                "PrevWeekFp"; "SleeperScore"
                "TotalFpY2"; "AvgFpY2"; "MaxFpY2"; "MinFpY2"; "FpPerSnapY2"; "FpConsistencyY2"
                "TotalFantasyPointsY1"; "PpgY1"; "FpPerSnapY1"
                "W1SnapShare"; "Y2SnapShareChange"; "SlidingWindowAvgDelta"
                "MaxSnapPctY2"; "MinSnapPctY2"; "AvgSnapPctY2"
                "SnapPctChange"; "SnapPctVariance"; "SnapConsistencyY2"
                "TotalOffSnapsY2"; "TotalOffSnapsY1"; "AvgSnapPctY1"
                "Height"; "Weight"; "Age"
                "RbSizeScore"; "WrHeightScore"; "TeSizeScore"
                "DraftNumber"; "YearsExp"
                "GamesY1"; "RookieYearUsage"
                "OpponentRushDefRank"; "OpponentPassDefRank"
                "Ecr"; "PlayerAvailable"
                "DraftValueScore"; "PerformanceScore"; "AgeScore"
                "EcrScore"; "MatchupScore"; "SnapTrendScore"
                "TotalGamesY2"; "GamesPlayedY2"
            |]

        let pipeline =
            EstimatorChain()
                .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "PositionEncoded", inputColumnName = "Position"))
                .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "TeamEncoded", inputColumnName = "Team"))
                .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "OpponentEncoded", inputColumnName = "Opponent"))
                .Append(mlContext.Transforms.Concatenate("Features", Array.append featureColumns [| "PositionEncoded"; "TeamEncoded"; "OpponentEncoded" |]))
                .Append(mlContext.Transforms.NormalizeMinMax("Features"))
                .Append(mlContext.BinaryClassification.Trainers.LbfgsLogisticRegression(labelColumnName = "Label"))

        let model = pipeline.Fit(dataView)
        printfn "Training completed"
        printfn ""

        model

    /// Load prediction CSV file
    let private loadPredictionFile (filePath: string) : PlayerWeeklyStats list =
        printfn "Loading prediction file: %s" filePath

        let lines = File.ReadAllLines(filePath)
        if lines.Length < 2 then
            printfn "Warning: File has no data rows"
            []
        else
            let headers = lines.[0].Split(',')
            let players =
                lines.[1..]
                |> Array.map (fun line -> line.Split(','))
                |> Array.choose (parseRowForPrediction headers)
                |> Array.toList

            printfn "Loaded %d players for prediction" players.Length
            printfn ""
            players

    /// Make predictions
    let private makePredictions (mlContext: MLContext) (model: ITransformer) (players: PlayerWeeklyStats list) : PlayerPrediction list =
        printfn "Making predictions..."

        let modelInputs = players |> List.map toModelInput
        let dataView = mlContext.Data.LoadFromEnumerable(modelInputs)
        let predictions = model.Transform(dataView)

        // Extract predictions with probabilities
        let predictionEngine = mlContext.Model.CreatePredictionEngine<ModelInput, BinaryPrediction>(model)

        let results =
            players
            |> List.map (fun player ->
                let input = toModelInput player
                let prediction = predictionEngine.Predict(input)
                {
                    Stats = player
                    PredictedHit = prediction.PredictedLabel
                    Probability = prediction.Probability
                })

        let hitCount = results |> List.filter (fun r -> r.PredictedHit) |> List.length
        printfn "Predicted %d hits out of %d players" hitCount results.Length
        printfn ""

        results

    /// Generate HTML output with predictions
    let private generateHtmlOutput (predictions: PlayerPrediction list) (outputPath: string) : string =
        printfn "Generating HTML output..."

        // Extract season and week from first prediction
        let season = if predictions.IsEmpty then 0 else predictions.Head.Stats.Season
        let week = if predictions.IsEmpty then 0 else predictions.Head.Stats.AnalysisWeek
        let titleText = sprintf "NFL Fantasy Sleeper Hit Predictions - ML Model - %d Week %d" season week

        let headers = [
            "Player"; "Pos"; "Team"; "Opp"; "Sleeper Score";
            "Prev FP"; "Curr FP"; "FP Δ";
            "Snap%"; "Snap Δ"; "ECR"; "ML Hit Prediction"
        ]

        let tableRows =
            predictions
            |> List.sortByDescending (fun p -> p.Probability)
            |> List.map (fun p ->
                [
                    p.Stats.Player
                    p.Stats.Position
                    p.Stats.Team
                    p.Stats.Opponent
                    sprintf "%.1f" p.Stats.SleeperScore
                    sprintf "%.1f" p.Stats.PrevWeekFp
                    sprintf "%.1f" p.Stats.CurrentWeekFp
                    sprintf "%.1f" p.Stats.FpWeekDelta
                    sprintf "%.1f" p.Stats.AvgSnapPctY2
                    sprintf "%.1f" p.Stats.SnapPctChange
                    sprintf "%.0f" p.Stats.Ecr
                    if p.PredictedHit then sprintf "✓ (%.0f%%)" (p.Probability * 100.0f) else ""
                ])

        // Create color coding
        let whiteColorStr = "rgb(255, 255, 255)"
        let greenColorStr = "rgb(200, 255, 200)"

        // Get sleeper scores for heatmap
        let sleeperScores = predictions |> List.map (fun p -> float p.Stats.SleeperScore)
        let minScore = sleeperScores |> List.min
        let maxScore = sleeperScores |> List.max

        let getCellColor (v: float) =
            if maxScore = minScore then "rgb(240, 240, 255)"
            else
                let ratio = (v - minScore) / (maxScore - minScore)
                let r = int (200.0 + ratio * 55.0)
                let g = int (220.0 - ratio * 120.0)
                let b = int (255.0 - ratio * 155.0)
                sprintf "rgb(%d, %d, %d)" r g b

        let cellColorRows =
            predictions
            |> List.sortByDescending (fun p -> p.Probability)
            |> List.map (fun p ->
                [
                    whiteColorStr                                    // Player
                    whiteColorStr                                    // Pos
                    whiteColorStr                                    // Team
                    whiteColorStr                                    // Opp
                    getCellColor (float p.Stats.SleeperScore)       // Sleeper Score (heatmap)
                    whiteColorStr                                    // Prev FP
                    whiteColorStr                                    // Curr FP
                    whiteColorStr                                    // FP Δ
                    whiteColorStr                                    // Snap%
                    whiteColorStr                                    // Snap Δ
                    whiteColorStr                                    // ECR
                    if p.PredictedHit then greenColorStr else whiteColorStr  // ML Hit Prediction
                ])

        let cellcolor =
            cellColorRows
            |> Seq.map (fun row ->
                row
                |> Seq.map Color.fromString)
            |> Seq.transpose
            |> Seq.map Color.fromColors
            |> Color.fromColors

        let table =
            Chart.Table(
                headers,
                tableRows,
                UseDefaults = false,
                CellsMultiAlign = [
                    StyleParam.HorizontalAlign.Left      // Player
                    StyleParam.HorizontalAlign.Center    // Pos
                    StyleParam.HorizontalAlign.Center    // Team
                    StyleParam.HorizontalAlign.Center    // Opp
                    StyleParam.HorizontalAlign.Right     // Sleeper Score
                    StyleParam.HorizontalAlign.Right     // Prev FP
                    StyleParam.HorizontalAlign.Right     // Curr FP
                    StyleParam.HorizontalAlign.Right     // FP Δ
                    StyleParam.HorizontalAlign.Right     // Snap%
                    StyleParam.HorizontalAlign.Right     // Snap Δ
                    StyleParam.HorizontalAlign.Right     // ECR
                    StyleParam.HorizontalAlign.Center    // ML Hit Prediction
                ],
                CellsFillColor = cellcolor
            )
            |> Chart.withTitle titleText
            |> Chart.withConfigStyle(Responsive = true)
            |> Chart.withConfig(Config.init(
                ToImageButtonOptions = ConfigObjects.ToImageButtonOptions.init(
                    Format = StyleParam.ImageFormat.PNG,
                    Scale = 2.0
                )))

        // Expand tilde in path
        let expandedPath =
            if outputPath.StartsWith("~") then
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), outputPath.Substring(2))
            else
                outputPath

        // Ensure directory exists
        if not (Directory.Exists(expandedPath)) then
            Directory.CreateDirectory(expandedPath) |> ignore

        // Generate filename with timestamp
        let timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss")
        let filename = sprintf "ml_predictions_%s.html" timestamp
        let fullPath = Path.Combine(expandedPath, filename)

        // Save Plotly chart as HTML
        table |> Chart.saveHtml(fullPath)

        // Add custom CSS
        let htmlContent = File.ReadAllText(fullPath)
        let modifiedHtml = htmlContent.Replace("<body>", "<body><style>html, body, .js-plotly-plot, .plot-container, .svg-container { height: 100% !important; margin: 0; }</style>")
        File.WriteAllText(fullPath, modifiedHtml)

        printfn "HTML output saved to: %s" fullPath
        printfn ""

        fullPath

    /// Run prediction workflow
    let runPrediction (trainingDataFolder: string) (predictionFile: string) (outputPath: string) : int =
        try
            printfn ""
            printfn "=================================================="
            printfn "NFL Fantasy Sleeper Hit Prediction"
            printfn "ML Model - New Week Prediction"
            printfn "=================================================="
            printfn ""

            // Initialize ML.NET context
            let mlContext = MLContext(seed = Nullable 42)

            // Train model
            let model = trainModel mlContext trainingDataFolder

            // Load prediction file
            let players = loadPredictionFile predictionFile

            if players.IsEmpty then
                printfn "ERROR: No players loaded from prediction file"
                1
            else
                // Make predictions
                let predictions = makePredictions mlContext model players

                // Generate HTML output
                let htmlPath = generateHtmlOutput predictions outputPath

                printfn "=================================================="
                printfn "Prediction complete!"
                printfn "Open the HTML file in your browser to view results."
                printfn "=================================================="
                printfn ""

                0

        with ex ->
            printfn ""
            printfn "ERROR: %s" ex.Message
            printfn ""
            printfn "Stack trace:"
            printfn "%s" ex.StackTrace
            1
