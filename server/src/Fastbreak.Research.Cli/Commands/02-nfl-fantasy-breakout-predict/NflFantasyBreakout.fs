module Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict.NflFantasyBreakout

open Argu
open System
open System.IO
open System.Text.Json
open System.Text.Json.Serialization
open Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict.FantasyBreakoutTrainer
open Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict.MLNetBreakoutClassifier

type CsvInfo = {
    FileName: string
    FilePath: string
    FileSize: int64
    LineCount: int
    ColumnCount: int
    Columns: string[]
}

let countLinesInFile filePath =
    try
        File.ReadAllLines(filePath).Length
    with
    | ex ->
        printfn "Warning: Could not read %s - %s" filePath ex.Message
        0

let analyzeCsvFile filePath =
    try
        let fileName = Path.GetFileName(filePath : string)
        let fileInfo = FileInfo(filePath)
        let lines = File.ReadAllLines(filePath)

        if lines.Length = 0 then
            None
        else
            let headerLine = lines.[0]
            let columns = headerLine.Split(',')

            Some {
                FileName = fileName
                FilePath = filePath
                FileSize = fileInfo.Length
                LineCount = lines.Length
                ColumnCount = columns.Length
                Columns = columns
            }
    with
    | ex ->
        printfn "Error analyzing %s: %s" filePath ex.Message
        None

let formatFileSize (bytes: int64) =
    if bytes < 1024L then sprintf "%d B" bytes
    elif bytes < 1024L * 1024L then sprintf "%.1f KB" (float bytes / 1024.0)
    elif bytes < 1024L * 1024L * 1024L then sprintf "%.1f MB" (float bytes / (1024.0 * 1024.0))
    else sprintf "%.1f GB" (float bytes / (1024.0 * 1024.0 * 1024.0))

type WeeklyMetrics = {
    Week: int
    TopTenSleeperHits: int
    TopThreeSleeperHits: int
    MLModelSuccessfulHits: int
}

let extractWeekNumber (fileName: string) =
    try
        let parts = fileName.Split('_')
        let weekPart = parts |> Array.tryFind (fun p -> p.StartsWith("w"))
        match weekPart with
        | Some w ->
            let weekStr = w.Substring(1) // Remove 'w' prefix
            match System.Int32.TryParse(weekStr) with
            | true, weekNum -> Some weekNum
            | false, _ -> None
        | None -> None
    with
    | _ -> None

// Define types for JSON structure
type PlayerPrediction = {
    [<JsonPropertyName("player")>] Player: string
    [<JsonPropertyName("position")>] Position: string
    [<JsonPropertyName("team")>] Team: string
    [<JsonPropertyName("mlConfidence")>] MLConfidence: float32
    [<JsonPropertyName("sleeperScore")>] SleeperScore: float32
    [<JsonPropertyName("mlHit")>] MLHit: bool
    [<JsonPropertyName("sleeperHit")>] SleeperHit: bool
    [<JsonPropertyName("fpDelta")>] FpDelta: float32
}

type WeeklyPrediction = {
    [<JsonPropertyName("week")>] Week: int
    [<JsonPropertyName("totalPlayers")>] TotalPlayers: int
    [<JsonPropertyName("mlTop10Predictions")>] MLTop10Predictions: PlayerPrediction[]
    [<JsonPropertyName("sleeperTop10Predictions")>] SleeperTop10Predictions: PlayerPrediction[]
    [<JsonPropertyName("mlTop10Hits")>] MLTop10Hits: int
    [<JsonPropertyName("mlTop3Hits")>] MLTop3Hits: int
    [<JsonPropertyName("sleeperTop10Hits")>] SleeperTop10Hits: int
    [<JsonPropertyName("sleeperTop3Hits")>] SleeperTop3Hits: int
    [<JsonPropertyName("mlPrecisionTop10")>] MLPrecisionTop10: float
    [<JsonPropertyName("sleeperPrecisionTop10")>] SleeperPrecisionTop10: float
}

type CaseStudyExample = {
    [<JsonPropertyName("player")>] Player: string
    [<JsonPropertyName("position")>] Position: string
    [<JsonPropertyName("team")>] Team: string
    [<JsonPropertyName("week")>] Week: int
    [<JsonPropertyName("mlConfidence")>] MLConfidence: float32
    [<JsonPropertyName("sleeperScore")>] SleeperScore: float32
    [<JsonPropertyName("fpDelta")>] FpDelta: float32
    [<JsonPropertyName("analysis")>] Analysis: string
}

type CaseStudy = {
    [<JsonPropertyName("biggestMLSuccesses")>] BiggestMLSuccesses: CaseStudyExample[]
    [<JsonPropertyName("biggestSleeperMisses")>] BiggestSleeperMisses: CaseStudyExample[]
    [<JsonPropertyName("highConfidenceMLHits")>] HighConfidenceMLHits: CaseStudyExample[]
}

type OverallStats = {
    [<JsonPropertyName("totalAnalyzedGames")>] TotalAnalyzedGames: int
    [<JsonPropertyName("totalAnalyzedPlayers")>] TotalAnalyzedPlayers: int
    [<JsonPropertyName("totalWeeks")>] TotalWeeks: int
    [<JsonPropertyName("mlTotalTop10Hits")>] MLTotalTop10Hits: int
    [<JsonPropertyName("mlTotalTop10Opportunities")>] MLTotalTop10Opportunities: int
    [<JsonPropertyName("mlAccuracyTop10")>] MLAccuracyTop10: float
    [<JsonPropertyName("sleeperTotalTop10Hits")>] SleeperTotalTop10Hits: int
    [<JsonPropertyName("sleeperTotalTop10Opportunities")>] SleeperTotalTop10Opportunities: int
    [<JsonPropertyName("sleeperAccuracyTop10")>] SleeperAccuracyTop10: float
}

type ComprehensiveOutput = {
    [<JsonPropertyName("generatedAt")>] GeneratedAt: string
    [<JsonPropertyName("overallStats")>] OverallStats: OverallStats
    [<JsonPropertyName("caseStudy")>] CaseStudy: CaseStudy
    [<JsonPropertyName("weeklyPredictions")>] WeeklyPredictions: WeeklyPrediction[]
}

let generateComprehensiveJson (weeklyOutputs: WeeklyCsvOutput[]) (outputPath: string) =
    try
        printfn "\n=== GENERATING COMPREHENSIVE JSON OUTPUT ==="

        // Process each week's data
        let weeklyPredictions = ResizeArray<WeeklyPrediction>()
        let mutable totalMLHits = 0
        let mutable totalSleeperHits = 0
        let mutable totalMLOpportunities = 0
        let mutable totalSleeperOpportunities = 0
        let mutable totalPlayers = 0

        for output in weeklyOutputs |> Array.sortBy (fun o -> o.Week) do
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

            totalMLHits <- totalMLHits + mlTop10Hits
            totalSleeperHits <- totalSleeperHits + sleeperTop10Hits
            totalMLOpportunities <- totalMLOpportunities + mlTop10.Length
            totalSleeperOpportunities <- totalSleeperOpportunities + sleeperTop10.Length
            totalPlayers <- totalPlayers + output.Players.Length

            let mlPrecisionTop10 = if mlTop10.Length > 0 then float mlTop10Hits / float mlTop10.Length else 0.0
            let sleeperPrecisionTop10 = if sleeperTop10.Length > 0 then float sleeperTop10Hits / float sleeperTop10.Length else 0.0

            // Convert to JSON types
            let convertToJsonPlayer (p: PlayerPredictionOutput) =
                {
                    Player = p.Player
                    Position = p.Position
                    Team = p.Team
                    MLConfidence = p.MLConfidence
                    SleeperScore = p.SleeperScore
                    MLHit = p.MLHit
                    SleeperHit = p.SleeperHit
                    FpDelta = p.FpDelta
                }

            weeklyPredictions.Add({
                Week = output.Week
                TotalPlayers = output.Players.Length
                MLTop10Predictions = mlTop10 |> Array.map convertToJsonPlayer
                SleeperTop10Predictions = sleeperTop10 |> Array.map convertToJsonPlayer
                MLTop10Hits = mlTop10Hits
                MLTop3Hits = mlTop3Hits
                SleeperTop10Hits = sleeperTop10Hits
                SleeperTop3Hits = sleeperTop3Hits
                MLPrecisionTop10 = mlPrecisionTop10
                SleeperPrecisionTop10 = sleeperPrecisionTop10
            })

        let mlAccuracy = if totalMLOpportunities > 0 then float totalMLHits / float totalMLOpportunities else 0.0
        let sleeperAccuracy = if totalSleeperOpportunities > 0 then float totalSleeperHits / float totalSleeperOpportunities else 0.0

        // Generate case study examples
        let allPlayers = ResizeArray<{| Player: PlayerPredictionOutput; Week: int |}>()
        for output in weeklyOutputs do
            for player in output.Players do
                allPlayers.Add({| Player = player; Week = output.Week |})

        // Find biggest ML successes (High ML confidence AND they actually broke out with high FP delta)
        let biggestMLSuccesses =
            allPlayers
            |> Seq.filter (fun p -> p.Player.MLConfidence >= 0.45f && p.Player.ActualBreakout)
            |> Seq.sortByDescending (fun p -> p.Player.FpDelta)
            |> Seq.take (min 5 (allPlayers |> Seq.filter (fun p -> p.Player.MLConfidence >= 0.45f && p.Player.ActualBreakout) |> Seq.length))
            |> Seq.map (fun p -> {
                Player = p.Player.Player
                Position = p.Player.Position
                Team = p.Player.Team
                Week = p.Week
                MLConfidence = p.Player.MLConfidence
                SleeperScore = p.Player.SleeperScore
                FpDelta = p.Player.FpDelta
                Analysis = sprintf "ML model showed high confidence (%.1f%%) in this %.1f-point breakout while Sleeper score was %.0f"
                    (p.Player.MLConfidence * 100.0f)
                    p.Player.FpDelta
                    p.Player.SleeperScore
            })
            |> Array.ofSeq

        // Find biggest Sleeper misses (cases where sleeper didn't get the hit in top 10)
        let biggestSleeperMisses =
            allPlayers
            |> Seq.filter (fun p ->
                not p.Player.SleeperHit && p.Player.ActualBreakout && p.Player.FpDelta >= 15.0f)
            |> Seq.sortByDescending (fun p -> p.Player.FpDelta)
            |> Seq.take (min 5 (allPlayers |> Seq.filter (fun p ->
                not p.Player.SleeperHit && p.Player.ActualBreakout && p.Player.FpDelta >= 15.0f) |> Seq.length))
            |> Seq.map (fun p -> {
                Player = p.Player.Player
                Position = p.Player.Position
                Team = p.Player.Team
                Week = p.Week
                MLConfidence = p.Player.MLConfidence
                SleeperScore = p.Player.SleeperScore
                FpDelta = p.Player.FpDelta
                Analysis = if not p.Player.SleeperHit && p.Player.ActualBreakout then
                            sprintf "Sleeper missed this major %.1f-point breakout (score: %.0f) while ML model showed %.1f%% confidence"
                                p.Player.FpDelta p.Player.SleeperScore (p.Player.MLConfidence * 100.0f)
                           else
                            sprintf "Sleeper score was %.0f for this %.1f-point outcome while ML model showed %.1f%% confidence"
                                p.Player.SleeperScore p.Player.FpDelta (p.Player.MLConfidence * 100.0f)
            })
            |> Array.ofSeq

        // Find cases where ML model was significantly more confident than Sleeper score warranted
        let highConfidenceMLHits =
            allPlayers
            |> Seq.filter (fun p ->
                p.Player.ActualBreakout &&
                p.Player.MLConfidence >= 0.4f &&
                (p.Player.MLConfidence * 100.0f) > p.Player.SleeperScore)
            |> Seq.sortByDescending (fun p -> p.Player.MLConfidence - (p.Player.SleeperScore / 100.0f))
            |> Seq.take (min 5 (allPlayers |> Seq.filter (fun p ->
                p.Player.ActualBreakout &&
                p.Player.MLConfidence >= 0.4f &&
                (p.Player.MLConfidence * 100.0f) > p.Player.SleeperScore) |> Seq.length))
            |> Seq.map (fun p -> {
                Player = p.Player.Player
                Position = p.Player.Position
                Team = p.Player.Team
                Week = p.Week
                MLConfidence = p.Player.MLConfidence
                SleeperScore = p.Player.SleeperScore
                FpDelta = p.Player.FpDelta
                Analysis = sprintf "ML model showed better intuition: %.1f%% confidence vs Sleeper's %.0f score for this %.1f-point breakout"
                    (p.Player.MLConfidence * 100.0f)
                    p.Player.SleeperScore
                    p.Player.FpDelta
            })
            |> Array.ofSeq

        let caseStudy = {
            BiggestMLSuccesses = biggestMLSuccesses
            BiggestSleeperMisses = biggestSleeperMisses
            HighConfidenceMLHits = highConfidenceMLHits
        }

        let comprehensiveOutput = {
            GeneratedAt = System.DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss")
            OverallStats = {
                TotalAnalyzedGames = weeklyPredictions.Count
                TotalAnalyzedPlayers = totalPlayers
                TotalWeeks = weeklyPredictions.Count
                MLTotalTop10Hits = totalMLHits
                MLTotalTop10Opportunities = totalMLOpportunities
                MLAccuracyTop10 = mlAccuracy
                SleeperTotalTop10Hits = totalSleeperHits
                SleeperTotalTop10Opportunities = totalSleeperOpportunities
                SleeperAccuracyTop10 = sleeperAccuracy
            }
            CaseStudy = caseStudy
            WeeklyPredictions = weeklyPredictions.ToArray()
        }

        // Serialize to JSON with pretty printing
        let options = JsonSerializerOptions()
        options.WriteIndented <- true

        let jsonString = JsonSerializer.Serialize(comprehensiveOutput, options)
        File.WriteAllText(outputPath, jsonString)

        printfn "Comprehensive JSON output generated: %s" outputPath
        printfn "\n=== OVERALL STATISTICS ==="
        printfn "Total Analyzed Weeks: %d" comprehensiveOutput.OverallStats.TotalWeeks
        printfn "Total Analyzed Players: %d" comprehensiveOutput.OverallStats.TotalAnalyzedPlayers
        printfn "\nML Model Performance:"
        printfn "  Top 10 Hits: %d / %d (%.1f%% accuracy)"
            comprehensiveOutput.OverallStats.MLTotalTop10Hits
            comprehensiveOutput.OverallStats.MLTotalTop10Opportunities
            (comprehensiveOutput.OverallStats.MLAccuracyTop10 * 100.0)
        printfn "\nSleeper Score Performance:"
        printfn "  Top 10 Hits: %d / %d (%.1f%% accuracy)"
            comprehensiveOutput.OverallStats.SleeperTotalTop10Hits
            comprehensiveOutput.OverallStats.SleeperTotalTop10Opportunities
            (comprehensiveOutput.OverallStats.SleeperAccuracyTop10 * 100.0)

    with
    | ex ->
        printfn "Error generating comprehensive JSON: %s" ex.Message
        printfn "Stack trace: %s" ex.StackTrace

let runNflFantasyBreakout (args: ParseResults<'T>) =
    // Extract paths using manual argument parsing (similar to EloPlus)
    let argStrings = System.Environment.GetCommandLineArgs()
    let dataIndex = Array.tryFindIndex (fun s -> s = "--data" || s = "-d") argStrings
    let outputIndex = Array.tryFindIndex (fun s -> s = "--output" || s = "-o") argStrings

    match dataIndex, outputIndex with
    | Some i, Some o when i + 1 < argStrings.Length && o + 1 < argStrings.Length ->
        let dataPath = argStrings.[i + 1]
        let outputPath = argStrings.[o + 1]

        printfn "NFL Fantasy Breakout ML.NET Classification Analysis"
        printfn "===================================================="
        printfn "Data Directory: %s" dataPath
        printfn "Output Directory: %s" outputPath
        printfn ""

        if not (Directory.Exists(dataPath)) then
            printfn "ERROR: Directory does not exist: %s" dataPath
            1
        else
            // Get all CSV files
            let csvFiles = Directory.GetFiles(dataPath, "*.csv")

            if csvFiles.Length = 0 then
                printfn "No CSV files found in directory."
                1
            else
                printfn "Found %d CSV files" csvFiles.Length
                printfn ""

                // Separate training data (_raw.csv) and sleeper score files (.csv)
                let rawFiles = csvFiles |> Array.filter (fun f -> f.Contains("_raw.csv"))
                let sleeperFiles = csvFiles |> Array.filter (fun f -> f.EndsWith(".csv") && not (f.Contains("_raw.csv")))

                printfn "Training Data Files (*_raw.csv): %d" rawFiles.Length
                printfn "Sleeper Score Files (*.csv): %d" sleeperFiles.Length
                printfn ""

                // Analyze training data files
                if rawFiles.Length > 0 then
                    printfn "=== TRAINING DATA ANALYSIS ==="
                    let rawAnalysis = rawFiles |> Array.choose analyzeCsvFile

                    let totalRawRows = rawAnalysis |> Array.sumBy (fun info -> info.LineCount - 1) // -1 for header
                    let totalRawSize = rawAnalysis |> Array.sumBy (fun info -> info.FileSize)

                    printfn "Total training records: %d" totalRawRows
                    printfn "Total data size: %s" (formatFileSize totalRawSize)

                    if rawAnalysis.Length > 0 then
                        let firstFile = rawAnalysis.[0]
                        printfn "Features per record: %d" (firstFile.ColumnCount - 1) // -1 for player column
                        printfn ""
                        printfn "Sample features (first 10):"
                        firstFile.Columns |> Array.take (min 10 firstFile.Columns.Length) |> Array.iteri (fun i col -> printfn "  %d. %s" (i+1) col)
                        if firstFile.Columns.Length > 10 then
                            printfn "  ... and %d more features" (firstFile.Columns.Length - 10)

                    printfn ""
                    printfn "Week breakdown:"
                    rawAnalysis
                    |> Array.iter (fun info ->
                        let week = if info.FileName.Contains("_w") then
                                      let parts = info.FileName.Split('_')
                                      parts |> Array.tryFind (fun p -> p.StartsWith("w")) |> Option.defaultValue "unknown"
                                   else "unknown"
                        printfn "  %s: %d records (%s)" week (info.LineCount - 1) (formatFileSize info.FileSize))

                printfn ""

                // Analyze sleeper score files
                if sleeperFiles.Length > 0 then
                    printfn "=== SLEEPER SCORE ANALYSIS ==="
                    let sleeperAnalysis = sleeperFiles |> Array.choose analyzeCsvFile

                    let totalSleeperRows = sleeperAnalysis |> Array.sumBy (fun info -> info.LineCount - 1)
                    let totalSleeperSize = sleeperAnalysis |> Array.sumBy (fun info -> info.FileSize)

                    printfn "Total sleeper records: %d" totalSleeperRows
                    printfn "Total data size: %s" (formatFileSize totalSleeperSize)

                    if sleeperAnalysis.Length > 0 then
                        let firstFile = sleeperAnalysis.[0]
                        printfn "Columns per record: %d" firstFile.ColumnCount
                        printfn ""
                        printfn "Sleeper score columns:"
                        firstFile.Columns |> Array.iteri (fun i col -> printfn "  %d. %s" (i+1) col)

                    printfn ""
                    printfn "Week breakdown:"
                    sleeperAnalysis
                    |> Array.iter (fun info ->
                        let week = if info.FileName.Contains("_w") then
                                      let parts = info.FileName.Split('_')
                                      parts |> Array.tryFind (fun p -> p.StartsWith("w")) |> Option.defaultValue "unknown"
                                   else "unknown"
                        printfn "  %s: %d records (%s)" week (info.LineCount - 1) (formatFileSize info.FileSize))

                printfn ""
                printfn "=== SUMMARY ==="
                printfn "Ready for ML.NET training:"
                printfn "- Training features: %d files with %d total records" rawFiles.Length (if rawFiles.Length > 0 then Array.sumBy (fun info -> info.LineCount - 1) (rawFiles |> Array.choose analyzeCsvFile) else 0)
                printfn "- Target labels: %d files with %d total records" sleeperFiles.Length (if sleeperFiles.Length > 0 then Array.sumBy (fun info -> info.LineCount - 1) (sleeperFiles |> Array.choose analyzeCsvFile) else 0)
                printfn ""

                // Train ML.NET binary classification model
                if rawFiles.Length > 0 then
                    try
                        // Create output directory if it doesn't exist
                        let outputDir = Path.GetDirectoryName(outputPath)
                        if not (String.IsNullOrEmpty(outputDir)) && not (Directory.Exists(outputDir)) then
                            Directory.CreateDirectory(outputDir) |> ignore

                        printfn "=== STARTING ML.NET BINARY CLASSIFICATION ==="

                        // Train the ML.NET classifier and get weekly outputs
                        let (weeklyOutputs, accuracy, f1Score) = trainMLNetClassifier rawFiles outputDir

                        // Generate comprehensive JSON output only
                        generateComprehensiveJson weeklyOutputs outputPath

                        printfn "\n=== FINAL RESULTS ==="
                        printfn "ML.NET Model Performance:"
                        printfn "- Test Accuracy: %.2f%%" (accuracy * 100.0)
                        printfn "- F1 Score: %.3f" f1Score
                        printfn ""
                        printfn "Output File:"
                        printfn "- Comprehensive JSON: %s" outputPath

                        // Also run the comparison with simple models for reference
                        printfn "\n=== COMPARISON WITH SIMPLE MODELS ==="
                        let (simpleResult, _) = trainModel rawFiles sleeperFiles
                        printfn "Simple ML Model Accuracy: %.2f%%" (simpleResult.TestAccuracy * 100.0)
                        printfn "Sleeper Score Baseline: %.2f%%" (simpleResult.Precision * 100.0)

                        0
                    with
                    | ex ->
                        printfn "ERROR during ML.NET training: %s" ex.Message
                        printfn "Stack trace: %s" ex.StackTrace
                        1
                else
                    printfn "No training data files found for ML training."
                    0
    | _, _ ->
        printfn "ERROR: Both --data and --output arguments are required"
        printfn "Usage: dotnet run 02-nfl-fantasy-breakout --data /path/to/csv/directory --output /path/to/output/directory"
        printfn ""
        printfn "Arguments:"
        printfn "  --data, -d     Directory containing CSV files from the fantasy breakout prediction pipeline"
        printfn "  --output, -o   Directory where the weekly CSV files and summary report should be saved"
        1