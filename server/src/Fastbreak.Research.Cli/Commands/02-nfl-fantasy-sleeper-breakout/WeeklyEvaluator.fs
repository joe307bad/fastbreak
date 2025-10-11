namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO
open System.Diagnostics
open Microsoft.ML
open Microsoft.ML.Data
open DataTypes
open DataLoader

module WeeklyEvaluator =

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
    }

    /// Train model on all data
    let private trainModelOnAllData (mlContext: MLContext) (allData: IDataView) =
        printfn "Training LbfgsLogisticRegression on all data..."
        let stopwatch = Stopwatch.StartNew()

        // Get feature columns
        let featureColumns =
            [|
                "PrevWeekFp"; "CurrentWeekFp"; "SleeperScore"; "FpWeekDelta"
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

        // Convert to model inputs
        let modelInputs = weekData |> List.map toModelInput
        let dataView = mlContext.Data.LoadFromEnumerable(modelInputs)

        // Make predictions
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

        // Count actual and predicted hits
        let actualHits = weekData |> List.filter (fun p -> p.Hit) |> List.length
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

    /// Run weekly evaluation
    let runWeeklyEvaluation (dataFolder: string) : int =
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
