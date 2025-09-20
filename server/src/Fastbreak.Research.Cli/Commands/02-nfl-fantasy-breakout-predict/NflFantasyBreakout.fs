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

let runNflFantasyBreakout (args: ParseResults<'T>) =
    // Extract data path using manual argument parsing for now
    let argStrings = System.Environment.GetCommandLineArgs()
    let dataIndex = Array.tryFindIndex (fun s -> s = "--data" || s = "-d") argStrings
    match dataIndex with
    | Some i when i + 1 < argStrings.Length ->
        let dataPath = argStrings.[i + 1]
        printfn "NFL Fantasy Breakout ML Data Analysis"
        printfn "======================================"
        printfn "Data Directory: %s" dataPath
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

                        0
                    with
                    | ex ->
                        printfn "ERROR during ML training: %s" ex.Message
                        printfn "Stack trace: %s" ex.StackTrace
                        1
                else
                    printfn "No training data files found for ML training."
                    0
    | None ->
        printfn "ERROR: --data argument is required"
        printfn "Usage: dotnet run 02-nfl-fantasy-breakout --data /path/to/csv/directory"
        1