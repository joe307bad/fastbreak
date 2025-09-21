module Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict.NflFantasyBreakout

open Argu
open System
open System.IO
open Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict.FantasyBreakoutTrainer

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

let generateWeeklyMetricsJson (rawFiles: string[]) (sleeperFiles: string[]) (outputPath: string) =
    try
        printfn "\n=== GENERATING WEEKLY METRICS JSON ==="

        // Group files by week
        let weeklyRawFiles =
            rawFiles
            |> Array.choose (fun f ->
                match extractWeekNumber (Path.GetFileName(f)) with
                | Some week -> Some (week, f)
                | None -> None)
            |> Array.groupBy fst
            |> Map.ofArray

        let weeklySleeperFiles =
            sleeperFiles
            |> Array.choose (fun f ->
                match extractWeekNumber (Path.GetFileName(f)) with
                | Some week -> Some (week, f)
                | None -> None)
            |> Array.groupBy fst
            |> Map.ofArray

        let allWeeks =
            [weeklyRawFiles.Keys; weeklySleeperFiles.Keys]
            |> Seq.concat
            |> Set.ofSeq
            |> Set.toArray
            |> Array.sort

        let weeklyMetrics = ResizeArray<WeeklyMetrics>()

        for week in allWeeks do
            printfn "Processing week %d..." week

            let topTenHits = ref 0
            let topThreeHits = ref 0
            let mlHits = ref 0

            // Process sleeper score files for this week
            match weeklySleeperFiles.TryFind week with
            | Some weekFiles ->
                for (_, sleeperFile) in weekFiles do
                    try
                        let lines = File.ReadAllLines(sleeperFile)
                        if lines.Length > 1 then
                            let header = lines.[0].Split(',')
                            let hitStatusIdx = Array.tryFindIndex ((=) "hit_status") header
                            let sleeperRankIdx = Array.tryFindIndex ((=) "sleeper_rank") header

                            match hitStatusIdx, sleeperRankIdx with
                            | Some hitIdx, Some rankIdx ->
                                let allHits =
                                    lines.[1..]
                                    |> Array.choose (fun line ->
                                        let fields = line.Split(',')
                                        if fields.Length > max hitIdx rankIdx then
                                            let hitStatus = if hitIdx < fields.Length then fields.[hitIdx].Trim() else ""
                                            let isHit = hitStatus = "HIT"
                                            let rank =
                                                match System.Int32.TryParse(fields.[rankIdx]) with
                                                | true, r -> Some r
                                                | false, _ -> None
                                            match rank with
                                            | Some r when isHit -> Some (r, isHit)
                                            | _ -> None
                                        else None)

                                // Count hits in top 10 and top 3 ranks
                                let top10Hits = allHits |> Array.filter (fun (rank, _) -> rank <= 10) |> Array.length
                                let top3Hits = allHits |> Array.filter (fun (rank, _) -> rank <= 3) |> Array.length

                                topTenHits := !topTenHits + top10Hits
                                topThreeHits := !topThreeHits + top3Hits
                            | _ ->
                                printfn "Warning: hit_status or sleeper_rank column not found in %s" sleeperFile
                    with
                    | ex -> printfn "Error processing sleeper file %s: %s" sleeperFile ex.Message
            | None -> ()

            // Process ML model predictions for this week
            match weeklyRawFiles.TryFind week with
            | Some weekFiles ->
                let weeklyMLPredictions = ResizeArray<{| CombinedScore: float32; ActualBreakout: bool |}>()

                for (_, rawFile) in weekFiles do
                    try
                        let lines = File.ReadAllLines(rawFile)
                        if lines.Length > 1 then
                            let header = lines.[0].Split(',')
                            let fpDeltaIdx = Array.tryFindIndex ((=) "fp_delta") header
                            let sleeperScoreIdx = Array.tryFindIndex ((=) "sleeper_score") header
                            let prevWeekFpIdx = Array.tryFindIndex ((=) "prev_week_fp") header

                            match fpDeltaIdx, sleeperScoreIdx, prevWeekFpIdx with
                            | Some fpIdx, Some sleeperIdx, Some prevFpIdx ->
                                for line in lines.[1..] do
                                    let fields = line.Split(',')
                                    if fields.Length > max fpIdx (max sleeperIdx prevFpIdx) then
                                        let fpDelta =
                                            match System.Single.TryParse(fields.[fpIdx]) with
                                            | true, x -> x
                                            | false, _ -> 0.0f

                                        let sleeperScore =
                                            match System.Single.TryParse(fields.[sleeperIdx]) with
                                            | true, x -> x
                                            | false, _ -> 0.0f

                                        let prevWeekFp =
                                            match System.Single.TryParse(fields.[prevFpIdx]) with
                                            | true, x -> x
                                            | false, _ -> 0.0f

                                        // Apply simple ML model logic
                                        let snapScore = if prevWeekFp > 0.0f then 1.0f else 0.0f
                                        let momentumScore = if fpDelta > 0.0f then 1.0f else 0.0f
                                        let sleeperNormalized = sleeperScore / 200.0f
                                        let combinedScore = (sleeperNormalized * 0.6f) + (snapScore * 0.2f) + (momentumScore * 0.2f)
                                        let actualBreakout = fpDelta >= 5.0f // Using same threshold as trainer

                                        // Store all predictions with their scores for ranking
                                        weeklyMLPredictions.Add({| CombinedScore = combinedScore; ActualBreakout = actualBreakout |})
                            | _ -> ()
                    with
                    | ex -> printfn "Error processing raw file %s: %s" rawFile ex.Message

                // Sort by combined score descending and take top 10 predictions (like sleeper top 10)
                let allPredictionsArray = weeklyMLPredictions.ToArray()
                let top10MLPredictions =
                    allPredictionsArray
                    |> Array.sortByDescending (fun p -> p.CombinedScore)
                    |> Array.take (min 10 allPredictionsArray.Length)

                // Count hits only from top 10 ML predictions (fair comparison with sleeper top 10)
                let top10MLHits =
                    top10MLPredictions
                    |> Array.filter (fun p -> p.ActualBreakout)
                    |> Array.length

                mlHits := !mlHits + top10MLHits
            | None -> ()

            weeklyMetrics.Add({
                Week = week
                TopTenSleeperHits = !topTenHits
                TopThreeSleeperHits = !topThreeHits
                MLModelSuccessfulHits = !mlHits
            })

        // Write JSON file
        let jsonPath = outputPath
        let jsonObjects = ResizeArray<string>()

        for metrics in weeklyMetrics do
            let jsonObject = sprintf "        { Week: %d, TopTenSleeperHits: %d, TopThreeSleeperHits: %d, MLModelSuccessfulHits: %d }" metrics.Week metrics.TopTenSleeperHits metrics.TopThreeSleeperHits metrics.MLModelSuccessfulHits
            jsonObjects.Add(jsonObject)

        let jsonContent = "[\n" + String.concat ",\n" jsonObjects + "\n    ];"
        File.WriteAllText(jsonPath, jsonContent)

        printfn "Weekly metrics JSON generated: %s" jsonPath
        printfn "Contains %d weeks of data" weeklyMetrics.Count

        // Print summary
        printfn "\nWeekly Metrics Summary:"
        printfn "%-4s %-15s %-17s %-18s" "Week" "Top10 Sleeper" "Top3 Sleeper" "ML Model Hits"
        printfn "%s" (String.replicate 60 "-")
        for metrics in weeklyMetrics do
            printfn "%-4d %-15d %-17d %-18d" metrics.Week metrics.TopTenSleeperHits metrics.TopThreeSleeperHits metrics.MLModelSuccessfulHits

    with
    | ex ->
        printfn "Error generating weekly metrics JSON: %s" ex.Message
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

        printfn "NFL Fantasy Breakout ML Data Analysis"
        printfn "======================================"
        printfn "Data Directory: %s" dataPath
        printfn "Output CSV: %s" outputPath
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

                // Train ML.NET model
                if rawFiles.Length > 0 then
                    try
                        printfn "=== STARTING ML TRAINING ==="
                        let (result, predictions) = trainModel rawFiles sleeperFiles

                        printfn "\n=== FINAL RESULTS ==="
                        printfn "Model Performance:"
                        printfn "- Training Accuracy: %.1f%%" (result.TrainAccuracy * 100.0)
                        printfn "- Test Accuracy: %.1f%%" (result.TestAccuracy * 100.0)
                        printfn "- Precision: %.1f%%" (result.Precision * 100.0)
                        printfn "- Recall: %.1f%%" (result.Recall * 100.0)
                        printfn "- F1 Score: %.3f" result.F1Score
                        printfn ""
                        printfn "Data Split:"
                        printfn "- Training: %d records" result.TrainCount
                        printfn "- Testing: %d records" result.TestCount
                        printfn "- Breakouts in test set: %d" result.BreakoutCount

                        // Print prediction analysis
                        printPredictionSummary predictions

                        // Generate weekly metrics JSON
                        generateWeeklyMetricsJson rawFiles sleeperFiles outputPath

                        0
                    with
                    | ex ->
                        printfn "ERROR during ML training: %s" ex.Message
                        printfn "Stack trace: %s" ex.StackTrace
                        1
                else
                    printfn "No training data files found for ML training."
                    0
    | _, _ ->
        printfn "ERROR: Both --data and --output arguments are required"
        printfn "Usage: dotnet run 02-nfl-fantasy-breakout --data /path/to/csv/directory --output /path/to/output.json"
        printfn ""
        printfn "Arguments:"
        printfn "  --data, -d     Directory containing CSV files from the fantasy breakout prediction pipeline"
        printfn "  --output, -o   Path where the weekly metrics JSON file should be saved"
        1