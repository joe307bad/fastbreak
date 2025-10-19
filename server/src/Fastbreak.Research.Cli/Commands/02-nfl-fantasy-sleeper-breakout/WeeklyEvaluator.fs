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
        ActualHit: bool  // Whether the player actually had a breakout hit
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

    /// Get feature columns used in the model
    let private getFeatureColumns () =
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

    /// Build the training pipeline
    let private buildPipeline (mlContext: MLContext) =
        let featureColumns = getFeatureColumns()
        EstimatorChain()
            .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "PositionEncoded", inputColumnName = "Position"))
            .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "TeamEncoded", inputColumnName = "Team"))
            .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "OpponentEncoded", inputColumnName = "Opponent"))
            .Append(mlContext.Transforms.Concatenate("Features", Array.append featureColumns [| "PositionEncoded"; "TeamEncoded"; "OpponentEncoded" |]))
            .Append(mlContext.Transforms.NormalizeMinMax("Features"))
            .Append(mlContext.BinaryClassification.Trainers.LbfgsLogisticRegression(labelColumnName = "Label"))

    /// Train model on provided data (excluding a specific week for leave-one-out)
    let private trainModel (mlContext: MLContext) (trainingData: IDataView) (excludedWeek: string) =
        printfn "  Training model (excluding %s)..." excludedWeek
        let pipeline = buildPipeline mlContext
        pipeline.Fit(trainingData)

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
                    ActualHit = player.Hit  // Store the actual hit status
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

            // Calculate average binary classification metrics
            let avgAccuracy = results |> List.averageBy (fun r -> r.Accuracy)
            let avgPrecision = results |> List.averageBy (fun r -> r.Precision)
            let avgRecall = results |> List.averageBy (fun r -> r.Recall)
            let avgF1Score = results |> List.averageBy (fun r -> r.F1Score)

            // Calculate seasonal breakdowns (early: weeks 3-6, mid: weeks 7-12, late: weeks 13-18)
            let calculateSeasonalStats (weekRange: int * int) =
                let (minWeek, maxWeek) = weekRange
                let seasonalResults =
                    results
                    |> List.filter (fun r ->
                        let (_, week) = extractYearAndWeek r.Week
                        week >= minWeek && week <= maxWeek)

                let mlHits =
                    seasonalResults
                    |> List.sumBy (fun r ->
                        let top10 = r.PlayerPredictions |> List.sortByDescending (fun p -> p.MlConfidence) |> List.truncate 10
                        top10 |> List.filter (fun p -> p.ActualHit) |> List.length)

                let sleeperHits =
                    seasonalResults
                    |> List.sumBy (fun r ->
                        let top10 = r.PlayerPredictions |> List.sortByDescending (fun p -> p.SleeperScore) |> List.truncate 10
                        top10 |> List.filter (fun p -> p.ActualHit) |> List.length)

                (mlHits, sleeperHits)

            let (earlyMlHits, earlySleeperHits) = calculateSeasonalStats (3, 6)
            let (midMlHits, midSleeperHits) = calculateSeasonalStats (7, 12)
            let (lateMlHits, lateSleeperHits) = calculateSeasonalStats (13, 18)

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
            sb.AppendFormat("    \"overallMlPredictionAccuracy\": {0:0.0000000000000000},\n", overallMlPredictionAccuracy) |> ignore
            sb.AppendLine("    \"binaryClassificationMetrics\": {") |> ignore
            sb.AppendFormat("      \"avgAccuracy\": {0:0.0000000000000000},\n", avgAccuracy) |> ignore
            sb.AppendFormat("      \"avgPrecision\": {0:0.0000000000000000},\n", avgPrecision) |> ignore
            sb.AppendFormat("      \"avgRecall\": {0:0.0000000000000000},\n", avgRecall) |> ignore
            sb.AppendFormat("      \"avgF1Score\": {0:0.0000000000000000}\n", avgF1Score) |> ignore
            sb.AppendLine("    },") |> ignore
            sb.AppendLine("    \"seasonalBreakdown\": {") |> ignore
            sb.AppendLine("      \"earlySeason\": {") |> ignore
            sb.AppendLine("        \"weeks\": \"3-6\",") |> ignore
            sb.AppendFormat("        \"mlTop10Hits\": {0},\n", earlyMlHits) |> ignore
            sb.AppendFormat("        \"sleeperTop10Hits\": {0}\n", earlySleeperHits) |> ignore
            sb.AppendLine("      },") |> ignore
            sb.AppendLine("      \"midSeason\": {") |> ignore
            sb.AppendLine("        \"weeks\": \"7-12\",") |> ignore
            sb.AppendFormat("        \"mlTop10Hits\": {0},\n", midMlHits) |> ignore
            sb.AppendFormat("        \"sleeperTop10Hits\": {0}\n", midSleeperHits) |> ignore
            sb.AppendLine("      },") |> ignore
            sb.AppendLine("      \"lateSeason\": {") |> ignore
            sb.AppendLine("        \"weeks\": \"13-18\",") |> ignore
            sb.AppendFormat("        \"mlTop10Hits\": {0},\n", lateMlHits) |> ignore
            sb.AppendFormat("        \"sleeperTop10Hits\": {0}\n", lateSleeperHits) |> ignore
            sb.AppendLine("      }") |> ignore
            sb.AppendLine("    }") |> ignore
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

                // Count how many of the top recommendations actually had hits
                let mlTop10Hits = top10ML |> List.filter (fun p -> p.ActualHit) |> List.length
                let mlTop3Hits = top10ML |> List.truncate 3 |> List.filter (fun p -> p.ActualHit) |> List.length
                let sleeperTop10Hits = top10Sleeper |> List.filter (fun p -> p.ActualHit) |> List.length
                let sleeperTop3Hits = top10Sleeper |> List.truncate 3 |> List.filter (fun p -> p.ActualHit) |> List.length

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
                sb.AppendLine("      \"sleeperTop10Predictions\": [") |> ignore

                // Add sleeper player predictions
                top10Sleeper |> List.iteri (fun j p ->
                    playerToJson sb p
                    if j < top10Sleeper.Length - 1 then sb.AppendLine(",") |> ignore else sb.AppendLine() |> ignore
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

            // Generate filename
            let filename = "output.json"
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
            printSectionHeader "NFL Fantasy Sleeper Hit Prediction\nWeekly Evaluation Results (Leave-One-Out Cross-Validation)"

            // Initialize ML.NET context
            let mlContext = MLContext(seed = Nullable 42)

            // Get all CSV files, excluding 2025 data
            let csvFiles =
                Directory.GetFiles(dataFolder, "*.csv")
                |> Array.filter (fun file -> not (file.Contains("2025")))
                |> Array.sort

            if csvFiles.Length = 0 then
                printfn "ERROR: No data files found in folder: %s" dataFolder
                1
            else
                printfn "Found %d weeks of data for evaluation" csvFiles.Length
                printfn "Using leave-one-out cross-validation to prevent data leakage..."
                printfn ""

                // Load all data by week
                let allWeeksData =
                    csvFiles
                    |> Array.map (fun file ->
                        let fileName = Path.GetFileName(file)
                        let lines = File.ReadAllLines(file)
                        if lines.Length < 2 then
                            (fileName, [])
                        else
                            let headers = lines.[0].Split(',')
                            let parseRow = DataLoader.parseRow headers
                            let weekData =
                                lines.[1..]
                                |> Array.map (fun line -> line.Split(','))
                                |> Array.choose parseRow
                                |> Array.toList
                            (fileName, weekData))
                    |> Array.filter (fun (_, data) -> not data.IsEmpty)

                printfn "Evaluating each week using model trained on other weeks..."
                printfn ""

                // Evaluate each week separately using leave-one-out cross-validation
                let results =
                    allWeeksData
                    |> Array.map (fun (testFileName, testWeekData) ->
                        printfn "Evaluating %s..." testFileName

                        // Get training data (all weeks except the test week)
                        let trainingData =
                            allWeeksData
                            |> Array.filter (fun (fileName, _) -> fileName <> testFileName)
                            |> Array.collect (fun (_, data) -> List.toArray data)
                            |> Array.toList

                        if trainingData.IsEmpty then
                            printfn "  ERROR: No training data available"
                            None
                        else
                            // Convert training data to IDataView
                            let trainingInputs = trainingData |> List.map toModelInput
                            let trainingDataView = mlContext.Data.LoadFromEnumerable(trainingInputs)

                            // Train model on all weeks except this one
                            let model = trainModel mlContext trainingDataView testFileName

                            // Evaluate on the held-out week
                            let result = evaluateWeek mlContext model testFileName testWeekData
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
