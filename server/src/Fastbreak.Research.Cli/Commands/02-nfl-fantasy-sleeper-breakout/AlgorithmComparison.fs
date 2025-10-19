namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open Microsoft.ML
open DataTypes
open DataLoader
open AlgorithmTrainer

module AlgorithmComparison =

    /// Format a section header
    let private printSectionHeader (title: string) =
        printfn ""
        printfn "=================================================="
        printfn "%s" title
        printfn "=================================================="
        printfn ""

    /// Print dataset statistics
    let private printDatasetStats (stats: DatasetStats) =
        printfn "Dataset:"
        printfn "- Total Samples: %s" (stats.TotalSamples.ToString("N0"))
        printfn "- Training Samples: %s (80%%)" (stats.TrainingSamples.ToString("N0"))
        printfn "- Test Samples: %s (20%%)" (stats.TestSamples.ToString("N0"))
        printfn "- Hit Count: %s" (stats.HitCount.ToString("N0"))
        printfn "- Non-Hit Count: %s" (stats.NonHitCount.ToString("N0"))
        printfn "- Hit Rate: %.1f%%" (stats.HitRate * 100.0)
        printfn "- Feature Count: ~%d" stats.FeatureCount

    /// Print algorithm rankings
    let private printAlgorithmRankings (results: EvaluationMetrics list) =
        printfn "--------------------------------------------------"
        printfn "Algorithm Rankings (by AUC):"
        printfn "--------------------------------------------------"
        printfn ""

        results
        |> List.sortByDescending (fun r -> r.Auc)
        |> List.iteri (fun i result ->
            printfn "%d. %-25s AUC: %.3f" (i + 1) result.AlgorithmName result.Auc)

    /// Print detailed results for a single algorithm
    let private printDetailedResult (rank: int) (result: EvaluationMetrics) =
        printfn ""
        printfn "[%d] %s" rank result.AlgorithmName
        printfn "├─ AUC:           %.3f" result.Auc
        printfn "├─ Accuracy:      %.3f" result.Accuracy
        printfn "├─ Precision:     %.3f" result.Precision
        printfn "├─ Recall:        %.3f" result.Recall
        printfn "├─ F1 Score:      %.3f" result.F1Score
        printfn "├─ Training Time: %.1fs" result.TrainingTimeSeconds
        printfn "└─ Confusion Matrix:"
        printfn "   ├─ True Positives:  %d" result.ConfusionMatrix.TruePositives
        printfn "   ├─ False Positives: %d" result.ConfusionMatrix.FalsePositives
        printfn "   ├─ True Negatives:  %d" result.ConfusionMatrix.TrueNegatives
        printfn "   └─ False Negatives: %d" result.ConfusionMatrix.FalseNegatives

    /// Print all detailed results
    let private printDetailedResults (results: EvaluationMetrics list) =
        printfn ""
        printfn "--------------------------------------------------"
        printfn "Detailed Results:"
        printfn "--------------------------------------------------"

        results
        |> List.sortByDescending (fun r -> r.Auc)
        |> List.iteri (fun i result ->
            printDetailedResult (i + 1) result)

    /// Print recommendation
    let private printRecommendation (bestAlgorithm: EvaluationMetrics) =
        printfn ""
        printfn "--------------------------------------------------"
        printfn "Recommendation: Use %s for production" bestAlgorithm.AlgorithmName
        printfn "--------------------------------------------------"
        printfn ""

    /// Run complete algorithm comparison
    let runComparison (dataFolder: string) : int =
        try
            printSectionHeader "NFL Fantasy Sleeper Hit Prediction\nMulti-Algorithm Evaluation Results"

            // Initialize ML.NET context
            let mlContext = MLContext(seed = Nullable 42)

            // Load data
            printfn "Loading data..."
            let allData = loadAllData dataFolder

            if allData.IsEmpty then
                printfn "ERROR: No data loaded. Check data folder path."
                1
            else
                // Convert to IDataView
                let modelInputs = allData |> List.map toModelInput
                let dataView = mlContext.Data.LoadFromEnumerable(modelInputs)

                // Shuffle and split data 80/20
                let shuffled = mlContext.Data.ShuffleRows(dataView, seed = Nullable 42)
                let split = mlContext.Data.TrainTestSplit(shuffled, testFraction = 0.2, seed = Nullable 42)

                let trainData = split.TrainSet
                let testData = split.TestSet

                // Calculate statistics (approximate counts based on split ratio)
                let totalCount = allData.Length
                let trainCount = int (float totalCount * 0.8)
                let testCount = totalCount - trainCount
                let stats = calculateStats allData trainCount testCount

                printfn ""
                printDatasetStats stats

                // Train all algorithms
                let results = trainAllAlgorithms mlContext trainData testData

                // Sort results by AUC
                let sortedResults = results |> List.sortByDescending (fun r -> r.Auc)
                let bestAlgorithm = sortedResults |> List.head

                // Print results
                printfn ""
                printAlgorithmRankings sortedResults
                printDetailedResults sortedResults
                printRecommendation bestAlgorithm

                // Success
                0

        with ex ->
            printfn ""
            printfn "ERROR: %s" ex.Message
            printfn ""
            printfn "Stack trace:"
            printfn "%s" ex.StackTrace
            1
