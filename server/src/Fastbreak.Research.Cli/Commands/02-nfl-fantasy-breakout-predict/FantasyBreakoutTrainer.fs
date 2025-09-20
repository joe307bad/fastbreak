module Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict.FantasyBreakoutTrainer

open System
open System.IO

// Configuration constants
let private FANTASY_POINT_BREAKOUT_THRESHOLD = 5.0f

type SimplePlayer = {
    Player: string
    Position: string
    Team: string
    FpDelta: float32
    SleeperScore: float32
    PrevWeekFp: float32
    IsBreakout: bool
}

type TrainingResult = {
    TrainAccuracy: float
    TestAccuracy: float
    Precision: float
    Recall: float
    F1Score: float
    TrainCount: int
    TestCount: int
    BreakoutCount: int
}

type PredictionResult = {
    Player: string
    Position: string
    Team: string
    ActualBreakout: bool
    PredictedBreakout: bool
    Confidence: float32
    FpDelta: float32
    SleeperScore: float32
}

let private loadAndPrepareData (rawFilePaths: string[]) =
    let allPlayers = ResizeArray<SimplePlayer>()

    for path in rawFilePaths do
        printfn "Loading training data: %s" (Path.GetFileName(path))
        try
            let lines = File.ReadAllLines(path)
            if lines.Length > 1 then
                let header = lines.[0].Split(',')
                let playerIdx = Array.findIndex ((=) "player") header
                let positionIdx = Array.tryFindIndex ((=) "position") header |> Option.defaultValue 2
                let teamIdx = Array.tryFindIndex ((=) "team") header |> Option.defaultValue 3
                let fpDeltaIdx = Array.findIndex ((=) "fp_delta") header
                let sleeperScoreIdx = Array.findIndex ((=) "sleeper_score") header
                let prevWeekFpIdx = Array.findIndex ((=) "prev_week_fp") header

                for line in lines.[1..] do
                    let fields = line.Split(',')
                    if fields.Length > max fpDeltaIdx (max sleeperScoreIdx prevWeekFpIdx) then
                        let fpDelta =
                            match System.Single.TryParse(fields.[fpDeltaIdx]) with
                            | true, x -> x
                            | false, _ -> 0.0f

                        let sleeperScore =
                            match System.Single.TryParse(fields.[sleeperScoreIdx]) with
                            | true, x -> x
                            | false, _ -> 0.0f

                        let prevWeekFp =
                            match System.Single.TryParse(fields.[prevWeekFpIdx]) with
                            | true, x -> x
                            | false, _ -> 0.0f

                        allPlayers.Add({
                            Player = fields.[playerIdx]
                            Position = if positionIdx < fields.Length then fields.[positionIdx] else "Unknown"
                            Team = if teamIdx < fields.Length then fields.[teamIdx] else "Unknown"
                            FpDelta = fpDelta
                            SleeperScore = sleeperScore
                            PrevWeekFp = prevWeekFp
                            IsBreakout = fpDelta >= FANTASY_POINT_BREAKOUT_THRESHOLD
                        })
        with
        | ex -> printfn "Error reading %s: %s" path ex.Message

    allPlayers.ToArray()

let private splitTrainTest (data: SimplePlayer[]) =
    let random = System.Random(42)
    let shuffled = data |> Array.sortBy (fun _ -> random.Next())
    let splitIndex = int (float shuffled.Length * 0.7)

    let trainData = shuffled.[0..splitIndex-1]
    let testData = shuffled.[splitIndex..]

    printfn "\nData Split:"
    printfn "Training set: %d records (70%%)" trainData.Length
    printfn "Test set: %d records (30%%)" testData.Length

    (trainData, testData)

let private simpleSleeperModel (player: SimplePlayer) =
    // Simple rule-based model using sleeper score as primary predictor
    let threshold = 100.0f // Sleeper score threshold
    player.SleeperScore >= threshold

let private simpleMLModel (player: SimplePlayer) =
    // Simple ML-inspired model combining multiple factors
    let snapScore = if player.PrevWeekFp > 0.0f then 1.0f else 0.0f
    let momentumScore = if player.FpDelta > 0.0f then 1.0f else 0.0f
    let sleeperNormalized = player.SleeperScore / 200.0f // Normalize to 0-1

    // Weighted combination
    let combinedScore = (sleeperNormalized * 0.6f) + (snapScore * 0.2f) + (momentumScore * 0.2f)
    combinedScore >= 0.5f

let private calculateMetrics (predictions: PredictionResult[]) =
    let truePositives = predictions |> Array.filter (fun p -> p.ActualBreakout && p.PredictedBreakout) |> Array.length |> float
    let falsePositives = predictions |> Array.filter (fun p -> not p.ActualBreakout && p.PredictedBreakout) |> Array.length |> float
    let trueNegatives = predictions |> Array.filter (fun p -> not p.ActualBreakout && not p.PredictedBreakout) |> Array.length |> float
    let falseNegatives = predictions |> Array.filter (fun p -> p.ActualBreakout && not p.PredictedBreakout) |> Array.length |> float

    let accuracy = (truePositives + trueNegatives) / float predictions.Length
    let precision = if (truePositives + falsePositives) > 0.0 then truePositives / (truePositives + falsePositives) else 0.0
    let recall = if (truePositives + falseNegatives) > 0.0 then truePositives / (truePositives + falseNegatives) else 0.0
    let f1Score = if (precision + recall) > 0.0 then 2.0 * precision * recall / (precision + recall) else 0.0

    printfn "\n=== MODEL EVALUATION ==="
    printfn "Accuracy: %.3f" accuracy
    printfn "Precision: %.3f" precision
    printfn "Recall: %.3f" recall
    printfn "F1 Score: %.3f" f1Score
    printfn "Confusion Matrix:"
    printfn "  True Positives: %.0f" truePositives
    printfn "  False Positives: %.0f" falsePositives
    printfn "  True Negatives: %.0f" trueNegatives
    printfn "  False Negatives: %.0f" falseNegatives

    (accuracy, precision, recall, f1Score)

let trainModel (rawFilePaths: string[]) (sleeperFilePaths: string[]) =
    printfn "=== STARTING FANTASY BREAKOUT ANALYSIS ==="
    printfn "Fantasy Point Breakout Threshold: %.1f points" FANTASY_POINT_BREAKOUT_THRESHOLD
    printfn ""

    // Load and prepare data
    let data = loadAndPrepareData rawFilePaths

    printfn "Loaded %d total records" data.Length

    let breakoutCount = data |> Array.filter (fun x -> x.IsBreakout) |> Array.length
    let nonBreakoutCount = data.Length - breakoutCount

    printfn "Breakout threshold: %.1f fantasy points" FANTASY_POINT_BREAKOUT_THRESHOLD
    printfn "Breakout records: %d (%.1f%%)" breakoutCount (float breakoutCount / float data.Length * 100.0)
    printfn "Non-breakout records: %d (%.1f%%)" nonBreakoutCount (float nonBreakoutCount / float data.Length * 100.0)

    // Split data
    let (trainData, testData) = splitTrainTest data

    // Train on training data (in this simple case, just analyze the threshold)
    printfn "\n=== TRAINING MODELS ==="
    let trainBreakouts = trainData |> Array.filter (fun x -> x.IsBreakout) |> Array.length
    printfn "Training set breakouts: %d" trainBreakouts

    // Test SLEEPER SCORE MODEL
    printfn "\n=== TESTING SLEEPER SCORE MODEL ==="
    let sleeperTestPredictions =
        testData
        |> Array.map (fun player ->
            let predicted = simpleSleeperModel player
            {
                Player = player.Player
                Position = player.Position
                Team = player.Team
                ActualBreakout = player.IsBreakout
                PredictedBreakout = predicted
                Confidence = player.SleeperScore / 200.0f // Normalize sleeper score to 0-1
                FpDelta = player.FpDelta
                SleeperScore = player.SleeperScore
            })

    let (sleeperTestAccuracy, sleeperPrecision, sleeperRecall, sleeperF1Score) = calculateMetrics sleeperTestPredictions

    // Test ML MODEL
    printfn "\n=== TESTING ML MODEL ==="
    let mlTestPredictions =
        testData
        |> Array.map (fun player ->
            let predicted = simpleMLModel player
            let snapScore = if player.PrevWeekFp > 0.0f then 1.0f else 0.0f
            let momentumScore = if player.FpDelta > 0.0f then 1.0f else 0.0f
            let sleeperNormalized = player.SleeperScore / 200.0f
            let confidence = (sleeperNormalized * 0.6f) + (snapScore * 0.2f) + (momentumScore * 0.2f)

            {
                Player = player.Player
                Position = player.Position
                Team = player.Team
                ActualBreakout = player.IsBreakout
                PredictedBreakout = predicted
                Confidence = confidence
                FpDelta = player.FpDelta
                SleeperScore = player.SleeperScore
            })

    let (mlTestAccuracy, mlPrecision, mlRecall, mlF1Score) = calculateMetrics mlTestPredictions

    // Calculate training accuracy for both models
    let sleeperTrainPredictions =
        trainData
        |> Array.map (fun player ->
            let predicted = simpleSleeperModel player
            {
                Player = player.Player
                Position = player.Position
                Team = player.Team
                ActualBreakout = player.IsBreakout
                PredictedBreakout = predicted
                Confidence = player.SleeperScore / 200.0f
                FpDelta = player.FpDelta
                SleeperScore = player.SleeperScore
            })

    let mlTrainPredictions =
        trainData
        |> Array.map (fun player ->
            let predicted = simpleMLModel player
            let snapScore = if player.PrevWeekFp > 0.0f then 1.0f else 0.0f
            let momentumScore = if player.FpDelta > 0.0f then 1.0f else 0.0f
            let sleeperNormalized = player.SleeperScore / 200.0f
            let confidence = (sleeperNormalized * 0.6f) + (snapScore * 0.2f) + (momentumScore * 0.2f)

            {
                Player = player.Player
                Position = player.Position
                Team = player.Team
                ActualBreakout = player.IsBreakout
                PredictedBreakout = predicted
                Confidence = confidence
                FpDelta = player.FpDelta
                SleeperScore = player.SleeperScore
            })

    let (sleeperTrainAccuracy, _, _, _) = calculateMetrics sleeperTrainPredictions
    let (mlTrainAccuracy, _, _, _) = calculateMetrics mlTrainPredictions

    // Print comparison
    printfn "\n=== MODEL COMPARISON ==="
    printfn "                    Sleeper Score    ML Model    Difference"
    printfn "                    -------------    --------    ----------"
    printfn "Train Accuracy:     %.1f%%          %.1f%%       %+.1f%%" (sleeperTrainAccuracy * 100.0) (mlTrainAccuracy * 100.0) ((mlTrainAccuracy - sleeperTrainAccuracy) * 100.0)
    printfn "Test Accuracy:      %.1f%%          %.1f%%       %+.1f%%" (sleeperTestAccuracy * 100.0) (mlTestAccuracy * 100.0) ((mlTestAccuracy - sleeperTestAccuracy) * 100.0)
    printfn "Precision:          %.1f%%          %.1f%%       %+.1f%%" (sleeperPrecision * 100.0) (mlPrecision * 100.0) ((mlPrecision - sleeperPrecision) * 100.0)
    printfn "Recall:             %.1f%%          %.1f%%       %+.1f%%" (sleeperRecall * 100.0) (mlRecall * 100.0) ((mlRecall - sleeperRecall) * 100.0)
    printfn "F1 Score:           %.3f           %.3f        %+.3f" sleeperF1Score mlF1Score (mlF1Score - sleeperF1Score)

    // Determine winner
    printfn "\n=== METHODOLOGY WINNER ==="
    if mlTestAccuracy > sleeperTestAccuracy then
        printfn "🏆 ML MODEL WINS with %.1f%% test accuracy (vs %.1f%% sleeper score)" (mlTestAccuracy * 100.0) (sleeperTestAccuracy * 100.0)
        printfn "   Improvement: +%.1f%% accuracy, +%.1f%% precision, +%.1f%% recall"
            ((mlTestAccuracy - sleeperTestAccuracy) * 100.0)
            ((mlPrecision - sleeperPrecision) * 100.0)
            ((mlRecall - sleeperRecall) * 100.0)
    elif sleeperTestAccuracy > mlTestAccuracy then
        printfn "🏆 SLEEPER SCORE WINS with %.1f%% test accuracy (vs %.1f%% ML model)" (sleeperTestAccuracy * 100.0) (mlTestAccuracy * 100.0)
        printfn "   Advantage: +%.1f%% accuracy, +%.1f%% precision, +%.1f%% recall"
            ((sleeperTestAccuracy - mlTestAccuracy) * 100.0)
            ((sleeperPrecision - mlPrecision) * 100.0)
            ((sleeperRecall - mlRecall) * 100.0)
    else
        printfn "🤝 TIE - Both methodologies achieve %.1f%% test accuracy" (sleeperTestAccuracy * 100.0)

    printfn ""
    printfn "Analysis:"
    if sleeperF1Score > mlF1Score then
        printfn "• Sleeper Score provides better overall balance (higher F1 score)"
    elif mlF1Score > sleeperF1Score then
        printfn "• ML Model provides better overall balance (higher F1 score)"

    if sleeperPrecision > mlPrecision then
        printfn "• Sleeper Score is more conservative (fewer false positives)"
    elif mlPrecision > sleeperPrecision then
        printfn "• ML Model is more conservative (fewer false positives)"

    if sleeperRecall > mlRecall then
        printfn "• Sleeper Score catches more breakouts (fewer false negatives)"
    elif mlRecall > sleeperRecall then
        printfn "• ML Model catches more breakouts (fewer false negatives)"

    // Return the better performing model's results
    let betterResult =
        if mlTestAccuracy >= sleeperTestAccuracy then
            {
                TrainAccuracy = mlTrainAccuracy
                TestAccuracy = mlTestAccuracy
                Precision = mlPrecision
                Recall = mlRecall
                F1Score = mlF1Score
                TrainCount = trainData.Length
                TestCount = testData.Length
                BreakoutCount = testData |> Array.filter (fun p -> p.IsBreakout) |> Array.length
            }
        else
            {
                TrainAccuracy = sleeperTrainAccuracy
                TestAccuracy = sleeperTestAccuracy
                Precision = sleeperPrecision
                Recall = sleeperRecall
                F1Score = sleeperF1Score
                TrainCount = trainData.Length
                TestCount = testData.Length
                BreakoutCount = testData |> Array.filter (fun p -> p.IsBreakout) |> Array.length
            }

    let betterPredictions = if mlTestAccuracy >= sleeperTestAccuracy then mlTestPredictions else sleeperTestPredictions

    (betterResult, betterPredictions)

let printPredictionSummary (predictions: PredictionResult[]) =
    printfn "\n=== PREDICTION ANALYSIS ==="

    // Top predicted breakouts
    let topPredicted =
        predictions
        |> Array.filter (fun p -> p.PredictedBreakout)
        |> Array.sortByDescending (fun p -> p.Confidence)
        |> Array.take (min 10 predictions.Length)

    printfn "\nTop 10 Predicted Breakouts:"
    printfn "%-20s %-3s %-4s %-6s %-6s %-8s %-6s %-10s" "Player" "Pos" "Team" "Actual" "Pred" "Conf" "FP Δ" "Sleeper"
    printfn "%s" (String.replicate 80 "-")

    for pred in topPredicted do
        let actualStr = if pred.ActualBreakout then "✓" else "✗"
        let predStr = if pred.PredictedBreakout then "✓" else "✗"
        printfn "%-20s %-3s %-4s %-6s %-6s %-8.1f %-6.1f %-10.1f"
            pred.Player pred.Position pred.Team actualStr predStr (pred.Confidence * 100.0f) pred.FpDelta pred.SleeperScore

    // Missed breakouts (high FP delta but not predicted)
    let missedBreakouts =
        predictions
        |> Array.filter (fun p -> p.ActualBreakout && not p.PredictedBreakout)
        |> Array.sortByDescending (fun p -> p.FpDelta)
        |> Array.take (min 5 predictions.Length)

    if missedBreakouts.Length > 0 then
        printfn "\nTop 5 Missed Breakouts (Actual breakouts not predicted):"
        printfn "%-20s %-3s %-4s %-8s %-6s %-10s" "Player" "Pos" "Team" "Conf" "FP Δ" "Sleeper"
        printfn "%s" (String.replicate 65 "-")

        for pred in missedBreakouts do
            printfn "%-20s %-3s %-4s %-8.1f %-6.1f %-10.1f"
                pred.Player pred.Position pred.Team (pred.Confidence * 100.0f) pred.FpDelta pred.SleeperScore