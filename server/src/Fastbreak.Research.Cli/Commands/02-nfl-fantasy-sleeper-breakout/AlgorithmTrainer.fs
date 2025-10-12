namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.Diagnostics
open Microsoft.ML
open Microsoft.ML.Data
open DataTypes

module AlgorithmTrainer =

    /// Create feature concatenation pipeline
    let private createFeaturePipeline (mlContext: MLContext) featureColumns =
        EstimatorChain()
            .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "PositionEncoded", inputColumnName = "Position"))
            .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "TeamEncoded", inputColumnName = "Team"))
            .Append(mlContext.Transforms.Categorical.OneHotEncoding(outputColumnName = "OpponentEncoded", inputColumnName = "Opponent"))
            .Append(mlContext.Transforms.Concatenate("Features", Array.append featureColumns [| "PositionEncoded"; "TeamEncoded"; "OpponentEncoded" |]))
            .Append(mlContext.Transforms.NormalizeMinMax("Features"))

    /// Get all numeric feature column names
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

    /// Convert ML.NET metrics to our EvaluationMetrics type
    let private convertMetrics (algorithmName: string) (metrics: CalibratedBinaryClassificationMetrics) (trainingTime: float) : EvaluationMetrics =
        let confusionMatrix = metrics.ConfusionMatrix

        // Extract confusion matrix values
        let tp = int confusionMatrix.Counts.[0].[0]
        let fp = int confusionMatrix.Counts.[0].[1]
        let fn = int confusionMatrix.Counts.[1].[0]
        let tn = int confusionMatrix.Counts.[1].[1]

        // Calculate precision and recall
        let precision =
            if tp + fp = 0 then 0.0
            else float tp / float (tp + fp)

        let recall =
            if tp + fn = 0 then 0.0
            else float tp / float (tp + fn)

        // Calculate F1 score
        let f1Score =
            if precision + recall = 0.0 then 0.0
            else 2.0 * (precision * recall) / (precision + recall)

        {
            AlgorithmName = algorithmName
            Auc = metrics.AreaUnderRocCurve
            Accuracy = metrics.Accuracy
            Precision = precision
            Recall = recall
            F1Score = f1Score
            ConfusionMatrix = {
                TruePositives = tp
                FalsePositives = fp
                TrueNegatives = tn
                FalseNegatives = fn
            }
            TrainingTimeSeconds = trainingTime
        }

    /// Train and evaluate a specific algorithm
    let trainAndEvaluate (mlContext: MLContext) (algorithmName: string) (trainData: IDataView) (testData: IDataView) : EvaluationMetrics =
        printfn "  Training %s..." algorithmName

        let stopwatch = Stopwatch.StartNew()

        let featureColumns = getFeatureColumns()
        let featurePipeline = createFeaturePipeline mlContext featureColumns

        // Create trainer based on algorithm name
        let fullPipeline =
            match algorithmName with
            | "LbfgsLogisticRegression" ->
                featurePipeline.Append(mlContext.BinaryClassification.Trainers.LbfgsLogisticRegression(labelColumnName = "Label"))
            | "SdcaLogisticRegression" ->
                featurePipeline.Append(mlContext.BinaryClassification.Trainers.SdcaLogisticRegression(labelColumnName = "Label"))
            | _ -> failwithf "Unknown algorithm: %s" algorithmName

        // Train model
        let model = fullPipeline.Fit(trainData)
        stopwatch.Stop()

        let trainingTime = stopwatch.Elapsed.TotalSeconds
        printfn "    Training completed in %.2f seconds" trainingTime

        // Evaluate model
        let predictions = model.Transform(testData)
        let metrics = mlContext.BinaryClassification.Evaluate(predictions, labelColumnName = "Label")

        printfn "    AUC: %.3f" metrics.AreaUnderRocCurve

        convertMetrics algorithmName metrics trainingTime

    /// Train all algorithms
    let trainAllAlgorithms (mlContext: MLContext) (trainData: IDataView) (testData: IDataView) : EvaluationMetrics list =
        let algorithms = [
            "LbfgsLogisticRegression"
            "SdcaLogisticRegression"
        ]

        printfn ""
        printfn "Training algorithms..."
        printfn ""

        algorithms
        |> List.map (fun algo ->
            try
                trainAndEvaluate mlContext algo trainData testData
            with ex ->
                printfn "  ERROR training %s: %s" algo ex.Message
                {
                    AlgorithmName = algo
                    Auc = 0.0
                    Accuracy = 0.0
                    Precision = 0.0
                    Recall = 0.0
                    F1Score = 0.0
                    ConfusionMatrix = {
                        TruePositives = 0
                        FalsePositives = 0
                        TrueNegatives = 0
                        FalseNegatives = 0
                    }
                    TrainingTimeSeconds = 0.0
                })
