namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO
open System.Diagnostics
open System.Text
open Microsoft.ML
open Microsoft.ML.Data
open DataTypes
open DataLoader

module WeeklyEvaluator =

    /// Player prediction data
    type PlayerPrediction = {
        Player: string
        Position: string
        Team: string
        MlConfidence: float32
        SleeperScore: float32
        MlHit: bool
        SleeperHit: bool
        FpDelta: float32
    }

    /// Result for a single week's evaluation
    type WeekEvaluationResult = {
        FileName: string
        Week: string
        ActualHits: int
        PredictedHits: int
        TotalPlayers: int
        Accuracy: float
        Precision: float
        Recall: float
        F1Score: float
        TruePositives: int
        FalsePositives: int
        TrueNegatives: int
        FalseNegatives: int
        PlayerPredictions: PlayerPrediction list
    }

    /// Train model on all data
    let private trainModelOnAllData (mlContext: MLContext) (allData: IDataView) =
        printfn "Training LbfgsLogisticRegression on all data..."
        let stopwatch = Stopwatch.StartNew()

        // Get feature columns
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

        // Build pipeline
        let pipeline =
            EstimatorChain()
                .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "PositionEncoded", inputColumnName = "Position"))
                .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "TeamEncoded", inputColumnName = "Team"))
                .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "OpponentEncoded", inputColumnName = "Opponent"))
                .Append(mlContext.Transforms.Concatenate("Features", Array.append featureColumns [| "PositionEncoded"; "TeamEncoded"; "OpponentEncoded" |]))
                .Append(mlContext.Transforms.NormalizeMinMax("Features"))
                .Append(mlContext.BinaryClassification.Trainers.LbfgsLogisticRegression(labelColumnName = "Label"))

        let model = pipeline.Fit(allData)
        stopwatch.Stop()

        printfn "Training completed in %.2f seconds" stopwatch.Elapsed.TotalSeconds
        printfn ""

        model

    /// Evaluate model on a specific week's data
    let private evaluateWeek (mlContext: MLContext) (model: ITransformer) (fileName: string) (weekData: PlayerWeeklyStats list) : WeekEvaluationResult =
        let week = Path.GetFileNameWithoutExtension(fileName)

        // Count actual hits
        let actualHits = weekData |> List.filter (fun p -> p.Hit) |> List.length

        // Convert to model inputs and make predictions
        let modelInputs = weekData |> List.map toModelInput
        let dataView = mlContext.Data.LoadFromEnumerable(modelInputs)

        // Create prediction engine
        let predictionEngine = mlContext.Model.CreatePredictionEngine<ModelInput, ModelOutput>(model)

        // Generate player predictions with probabilities
        let playerPredictions =
            List.zip weekData modelInputs
            |> List.map (fun (player, input) ->
                let pred = predictionEngine.Predict(input)
                let sleeperHit = player.SleeperScore >= 100.0f
                {
                    Player = player.Player
                    Position = player.Position
                    Team = player.Team
                    MlConfidence = pred.Probability
                    SleeperScore = player.SleeperScore
                    MlHit = pred.PredictedLabel
                    SleeperHit = sleeperHit
                    FpDelta = if player.Hit then player.CurrentWeekFp - player.PrevWeekFp else 0.0f
                })

        // Check if there are no hits in the week - ML.NET can't evaluate without positive samples
        if actualHits = 0 then
            printfn "  Warning: Week has no hits (positive samples), skipping ML.NET evaluation"

            let predictedHits = playerPredictions |> List.filter (fun p -> p.MlHit) |> List.length
            let fp = predictedHits
            let tn = weekData.Length - fp

            {
                FileName = fileName
                Week = week
                ActualHits = 0
                PredictedHits = predictedHits
                TotalPlayers = weekData.Length
                Accuracy = float tn / float weekData.Length
                Precision = 0.0
                Recall = 0.0
                F1Score = 0.0
                TruePositives = 0
                FalsePositives = fp
                TrueNegatives = tn
                FalseNegatives = 0
                PlayerPredictions = playerPredictions
            }
        else
            // Normal evaluation with ML.NET
            let predictions = model.Transform(dataView)

            // Evaluate
            let metrics = mlContext.BinaryClassification.Evaluate(predictions, labelColumnName = "Label")
            let confusionMatrix = metrics.ConfusionMatrix

            // Extract confusion matrix values
            let tp = int confusionMatrix.Counts.[0].[0]
            let fp = int confusionMatrix.Counts.[0].[1]
            let fn = int confusionMatrix.Counts.[1].[0]
            let tn = int confusionMatrix.Counts.[1].[1]

            // Calculate metrics
            let precision =
                if tp + fp = 0 then 0.0
                else float tp / float (tp + fp)

            let recall =
                if tp + fn = 0 then 0.0
                else float tp / float (tp + fn)

            let f1Score =
                if precision + recall = 0.0 then 0.0
                else 2.0 * (precision * recall) / (precision + recall)

            let predictedHits = tp + fp

            {
                FileName = fileName
                Week = week
                ActualHits = actualHits
                PredictedHits = predictedHits
                TotalPlayers = weekData.Length
                Accuracy = metrics.Accuracy
                Precision = precision
                Recall = recall
                F1Score = f1Score
                TruePositives = tp
                FalsePositives = fp
                TrueNegatives = tn
                FalseNegatives = fn
                PlayerPredictions = playerPredictions
            }

    /// Print section header
    let private printSectionHeader (title: string) =
        printfn ""
        printfn "=================================================="
        printfn "%s" title
        printfn "=================================================="
        printfn ""

    /// Print week result
    let private printWeekResult (result: WeekEvaluationResult) =
        printfn "Week: %s" result.Week
        printfn "├─ Total Players:    %d" result.TotalPlayers
        printfn "├─ Actual Hits:      %d" result.ActualHits
        printfn "├─ Predicted Hits:   %d" result.PredictedHits
        printfn "├─ Accuracy:         %.3f" result.Accuracy
        printfn "├─ Precision:        %.3f" result.Precision
        printfn "├─ Recall:           %.3f" result.Recall
        printfn "├─ F1 Score:         %.3f" result.F1Score
        printfn "└─ Confusion Matrix:"
        printfn "   ├─ True Positives:  %d (correctly predicted hits)" result.TruePositives
        printfn "   ├─ False Positives: %d (incorrectly predicted as hits)" result.FalsePositives
        printfn "   ├─ True Negatives:  %d (correctly predicted non-hits)" result.TrueNegatives
        printfn "   └─ False Negatives: %d (missed actual hits)" result.FalseNegatives
        printfn ""

    /// Print summary statistics
    let private printSummary (results: WeekEvaluationResult list) =
        printfn "--------------------------------------------------"
        printfn "Summary Across All Weeks"
        printfn "--------------------------------------------------"
        printfn ""

        let totalPlayers = results |> List.sumBy (fun r -> r.TotalPlayers)
        let totalActualHits = results |> List.sumBy (fun r -> r.ActualHits)
        let totalPredictedHits = results |> List.sumBy (fun r -> r.PredictedHits)
        let avgAccuracy = results |> List.averageBy (fun r -> r.Accuracy)
        let avgPrecision = results |> List.averageBy (fun r -> r.Precision)
        let avgRecall = results |> List.averageBy (fun r -> r.Recall)
        let avgF1 = results |> List.averageBy (fun r -> r.F1Score)

        printfn "Total Players:         %d" totalPlayers
        printfn "Total Actual Hits:     %d" totalActualHits
        printfn "Total Predicted Hits:  %d" totalPredictedHits
        printfn "Average Accuracy:      %.3f" avgAccuracy
        printfn "Average Precision:     %.3f" avgPrecision
        printfn "Average Recall:        %.3f" avgRecall
        printfn "Average F1 Score:      %.3f" avgF1
        printfn ""

    /// Format a single player prediction as JSON
    let private playerToJson (sb: StringBuilder) (p: PlayerPrediction) =
        let mlHitStr = if p.MlHit then "true" else "false"
        let sleeperHitStr = if p.SleeperHit then "true" else "false"
        sb.AppendLine("        {") |> ignore
        sb.AppendFormat("          \"player\": \"{0}\",\n", p.Player) |> ignore
        sb.AppendFormat("          \"position\": \"{0}\",\n", p.Position) |> ignore
        sb.AppendFormat("          \"team\": \"{0}\",\n", p.Team) |> ignore
        sb.AppendFormat("          \"mlConfidence\": {0:0.0000000},\n", float p.MlConfidence) |> ignore
        sb.AppendFormat("          \"sleeperScore\": {0:0},\n", float p.SleeperScore) |> ignore
        sb.AppendFormat("          \"mlHit\": {0},\n", mlHitStr) |> ignore
        sb.AppendFormat("          \"sleeperHit\": {0},\n", sleeperHitStr) |> ignore
        sb.AppendFormat("          \"fpDelta\": {0:0.0}\n", float p.FpDelta) |> ignore
        sb.Append("        }") |> ignore

    /// Generate JSON output matching d3-charts/output.json structure
    let private generateJsonOutput (results: WeekEvaluationResult list) (outputPath: string) =
        try
            // Extract year and week number from filenames (e.g., "second_year_2024_week12" -> (2024, 12))
            let extractYearAndWeek (weekStr: string) =
                let parts = weekStr.Split('_')
                if parts.Length >= 4 then
                    // Pattern: second_year_YYYY_weekN
                    let yearPart = parts.[2]
                    let weekPart = parts.[3]
                    match Int32.TryParse(yearPart), Int32.TryParse(weekPart.Replace("week", "")) with
                    | (true, year), (true, week) -> (year, week)
                    | _ -> (0, 0)
                else (0, 0)

            // Calculate overall stats across all weeks
            let totalPlayers = results |> List.sumBy (fun r -> r.TotalPlayers)
            let totalWeeks = results.Length

            // Get top 10 ML predictions for each week and count hits
            let mlTop10HitsPerWeek =
                results
                |> List.map (fun r ->
                    let top10 = r.PlayerPredictions |> List.sortByDescending (fun p -> p.MlConfidence) |> List.truncate 10
                    top10 |> List.filter (fun p -> p.MlHit) |> List.length)

            let sleeperTop10HitsPerWeek =
                results
                |> List.map (fun r ->
                    let top10 = r.PlayerPredictions |> List.sortByDescending (fun p -> p.SleeperScore) |> List.truncate 10
                    top10 |> List.filter (fun p -> p.SleeperHit) |> List.length)

            let mlTotalTop10Hits = mlTop10HitsPerWeek |> List.sum
            let sleeperTotalTop10Hits = sleeperTop10HitsPerWeek |> List.sum
            let totalTop10Opportunities = totalWeeks * 10

            let mlAccuracyTop10 = if totalTop10Opportunities > 0 then float mlTotalTop10Hits / float totalTop10Opportunities else 0.0
            let sleeperAccuracyTop10 = if totalTop10Opportunities > 0 then float sleeperTotalTop10Hits / float totalTop10Opportunities else 0.0

            // Calculate overall ML prediction accuracy across all weeks
            let totalTruePositives = results |> List.sumBy (fun r -> r.TruePositives)
            let totalPredictedHits = results |> List.sumBy (fun r -> r.PredictedHits)
            let overallMlPredictionAccuracy =
                if totalPredictedHits > 0 then float totalTruePositives / float totalPredictedHits
                else 0.0

            // Find biggest ML successes (high confidence ML hits with big fantasy breakouts)
            let allPredictions =
                results
                |> List.mapi (fun idx r ->
                    let (year, week) = extractYearAndWeek r.Week
                    r.PlayerPredictions
                    |> List.map (fun p -> (year, week, p)))
                |> List.concat

            let biggestMLSuccesses =
                allPredictions
                |> List.filter (fun (_, _, p) -> p.MlHit && p.FpDelta > 0.0f)
                |> List.sortByDescending (fun (_, _, p) -> p.FpDelta)
                |> List.truncate 10

            // Build JSON using StringBuilder
            let sb = StringBuilder()
            let timestamp = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss")

            sb.AppendLine("{") |> ignore
            sb.AppendFormat("  \"generatedAt\": \"{0}\",\n", timestamp) |> ignore
            sb.AppendLine("  \"overallStats\": {") |> ignore
            sb.AppendFormat("    \"totalAnalyzedGames\": {0},\n", totalWeeks) |> ignore
            sb.AppendFormat("    \"totalAnalyzedPlayers\": {0},\n", totalPlayers) |> ignore
            sb.AppendFormat("    \"totalWeeks\": {0},\n", totalWeeks) |> ignore
            sb.AppendFormat("    \"mlTotalTop10Hits\": {0},\n", mlTotalTop10Hits) |> ignore
            sb.AppendFormat("    \"mlTotalTop10Opportunities\": {0},\n", totalTop10Opportunities) |> ignore
            sb.AppendFormat("    \"mlAccuracyTop10\": {0:0.0000000000000000},\n", mlAccuracyTop10) |> ignore
            sb.AppendFormat("    \"sleeperTotalTop10Hits\": {0},\n", sleeperTotalTop10Hits) |> ignore
            sb.AppendFormat("    \"sleeperTotalTop10Opportunities\": {0},\n", totalTop10Opportunities) |> ignore
            sb.AppendFormat("    \"sleeperAccuracyTop10\": {0:0.0000000000000000},\n", sleeperAccuracyTop10) |> ignore
            sb.AppendFormat("    \"overallMlPredictionAccuracy\": {0:0.0000000000000000}\n", overallMlPredictionAccuracy) |> ignore
            sb.AppendLine("  },") |> ignore
            sb.AppendLine("  \"caseStudy\": {") |> ignore
            sb.AppendLine("    \"biggestMLSuccesses\": [") |> ignore

            // Add biggest ML successes
            biggestMLSuccesses |> List.iteri (fun i (year, week, p) ->
                let analysis = sprintf "ML model predicted this %.1f-point breakout with %.1f%% confidence" (float p.FpDelta) (float p.MlConfidence * 100.0)
                sb.AppendLine("      {") |> ignore
                sb.AppendFormat("        \"player\": \"{0}\",\n", p.Player) |> ignore
                sb.AppendFormat("        \"position\": \"{0}\",\n", p.Position) |> ignore
                sb.AppendFormat("        \"team\": \"{0}\",\n", p.Team) |> ignore
                sb.AppendFormat("        \"year\": {0},\n", year) |> ignore
                sb.AppendFormat("        \"week\": {0},\n", week) |> ignore
                sb.AppendFormat("        \"mlConfidence\": {0:0.0000000},\n", float p.MlConfidence) |> ignore
                sb.AppendFormat("        \"sleeperScore\": {0:0},\n", float p.SleeperScore) |> ignore
                sb.AppendFormat("        \"fpDelta\": {0:0.0},\n", float p.FpDelta) |> ignore
                sb.AppendFormat("        \"analysis\": \"{0}\"\n", analysis) |> ignore
                sb.Append("      }") |> ignore
                if i < biggestMLSuccesses.Length - 1 then sb.AppendLine(",") |> ignore else sb.AppendLine() |> ignore
            )

            sb.AppendLine("    ],") |> ignore
            sb.AppendLine("    \"biggestSleeperMisses\": [],") |> ignore
            sb.AppendLine("    \"highConfidenceMLHits\": []") |> ignore
            sb.AppendLine("  },") |> ignore
            sb.AppendLine("  \"weeklyPredictions\": [") |> ignore

            // Generate weekly predictions
            results |> List.iteri (fun i r ->
                let (year, week) = extractYearAndWeek r.Week
                let top10ML = r.PlayerPredictions |> List.sortByDescending (fun p -> p.MlConfidence) |> List.truncate 10
                let top10Sleeper = r.PlayerPredictions |> List.sortByDescending (fun p -> p.SleeperScore) |> List.truncate 10

                let mlTop10Hits = top10ML |> List.filter (fun p -> p.MlHit) |> List.length
                let mlTop3Hits = top10ML |> List.truncate 3 |> List.filter (fun p -> p.MlHit) |> List.length
                let sleeperTop10Hits = top10Sleeper |> List.filter (fun p -> p.SleeperHit) |> List.length
                let sleeperTop3Hits = top10Sleeper |> List.truncate 3 |> List.filter (fun p -> p.SleeperHit) |> List.length

                let mlPrecision = if mlTop10Hits > 0 then float mlTop10Hits / 10.0 else 0.0
                let sleeperPrecision = if sleeperTop10Hits > 0 then float sleeperTop10Hits / 10.0 else 0.0

                // Calculate percentage of accurately predicted ML hits (precision for all predictions)
                let mlPredictedHitAccuracy =
                    if r.PredictedHits > 0 then float r.TruePositives / float r.PredictedHits
                    else 0.0

                sb.AppendLine("    {") |> ignore
                sb.AppendFormat("      \"year\": {0},\n", year) |> ignore
                sb.AppendFormat("      \"week\": {0},\n", week) |> ignore
                sb.AppendFormat("      \"totalPlayers\": {0},\n", r.TotalPlayers) |> ignore
                sb.AppendFormat("      \"mlTotalHits\": {0},\n", r.PredictedHits) |> ignore
                sb.AppendLine("      \"mlTop10Predictions\": [") |> ignore

                // Add player predictions
                top10ML |> List.iteri (fun j p ->
                    playerToJson sb p
                    if j < top10ML.Length - 1 then sb.AppendLine(",") |> ignore else sb.AppendLine() |> ignore
                )

                sb.AppendLine("      ],") |> ignore
                sb.AppendFormat("      \"mlTop10Hits\": {0},\n", mlTop10Hits) |> ignore
                sb.AppendFormat("      \"mlTop3Hits\": {0},\n", mlTop3Hits) |> ignore
                sb.AppendFormat("      \"sleeperTop10Hits\": {0},\n", sleeperTop10Hits) |> ignore
                sb.AppendFormat("      \"sleeperTop3Hits\": {0},\n", sleeperTop3Hits) |> ignore
                sb.AppendFormat("      \"mlPrecisionTop10\": {0:0.0},\n", mlPrecision) |> ignore
                sb.AppendFormat("      \"sleeperPrecisionTop10\": {0:0.0},\n", sleeperPrecision) |> ignore
                sb.AppendFormat("      \"mlPredictedHitAccuracy\": {0:0.000}\n", mlPredictedHitAccuracy) |> ignore
                sb.Append("    }") |> ignore
                if i < results.Length - 1 then sb.AppendLine(",") |> ignore else sb.AppendLine() |> ignore
            )

            sb.AppendLine("  ]") |> ignore
            sb.Append("}") |> ignore

            let json = sb.ToString()

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
            let filenameTimestamp = DateTime.Now.ToString("yyyy-MM-dd_HH-mm-ss")
            let filename = sprintf "output_%s.json" filenameTimestamp
            let fullPath = Path.Combine(expandedPath, filename)
            File.WriteAllText(fullPath, json)
            printfn ""
            printfn "JSON output saved to: %s" fullPath
            printfn ""
        with ex ->
            printfn "ERROR generating JSON output: %s" ex.Message

    /// Run weekly evaluation
    let runWeeklyEvaluation (dataFolder: string) (outputPath: string option) : int =
        try
            printSectionHeader "NFL Fantasy Sleeper Hit Prediction\nWeekly Evaluation Results"

            // Initialize ML.NET context
            let mlContext = MLContext(seed = Nullable 42)

            // Load all data for training
            printfn "Loading all data for training..."
            let allData = loadAllData dataFolder

            if allData.IsEmpty then
                printfn "ERROR: No data loaded. Check data folder path."
                1
            else
                printfn "Loaded %d total player-weeks from %d CSV files" allData.Length (Directory.GetFiles(dataFolder, "*.csv").Length)
                printfn ""

                // Convert to IDataView
                let modelInputs = allData |> List.map toModelInput
                let dataView = mlContext.Data.LoadFromEnumerable(modelInputs)

                // Train model on all data
                let model = trainModelOnAllData mlContext dataView

                // Get all CSV files
                let csvFiles = Directory.GetFiles(dataFolder, "*.csv") |> Array.sort

                printfn "Evaluating model on each week..."
                printfn ""

                // Evaluate each week separately
                let results =
                    csvFiles
                    |> Array.map (fun file ->
                        let fileName = Path.GetFileName(file)
                        printfn "Loading %s..." fileName

                        let lines = File.ReadAllLines(file)
                        if lines.Length < 2 then
                            printfn "  Skipping (no data)"
                            None
                        else
                            let headers = lines.[0].Split(',')
                            let parseRow = DataLoader.parseRow headers
                            let weekData =
                                lines.[1..]
                                |> Array.map (fun line -> line.Split(','))
                                |> Array.choose parseRow
                                |> Array.toList

                            if weekData.IsEmpty then
                                printfn "  Skipping (failed to parse)"
                                None
                            else
                                let result = evaluateWeek mlContext model fileName weekData
                                printWeekResult result
                                Some result)
                    |> Array.choose id
                    |> Array.toList

                if results.IsEmpty then
                    printfn "ERROR: No weeks were evaluated successfully."
                    1
                else
                    // Print summary
                    printSummary results

                    // Generate JSON output if requested
                    match outputPath with
                    | Some path -> generateJsonOutput results path
                    | None -> ()

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
