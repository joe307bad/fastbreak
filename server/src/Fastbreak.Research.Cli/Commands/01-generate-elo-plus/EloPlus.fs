namespace Fastbreak.Research.Cli.Commands.GenerateEloPlus

open System
open Argu
open Fastbreak.Research.Cli.Commands.GenerateEloPlus

module EloPlus =
    
    let printGameSummary (game: GameData) =
        let winner = if game.HomeScore > game.AwayScore then game.HomeTeam else game.AwayTeam
        let score = $"{game.HomeScore}-{game.AwayScore}"
        
        printfn "Game: %s vs %s (%s) - Winner: %s" 
            game.AwayTeam game.HomeTeam score winner
        
        
        match game.HomePitcher, game.AwayPitcher with
        | Some hp, Some ap ->
            printfn "  Pitchers: %s (ERA: %.2f) vs %s (ERA: %.2f)" 
                hp.Name hp.ERA ap.Name ap.ERA
        | _ -> printfn "  Pitchers: No data"
        
        match game.Metrics with
        | Some metrics ->
            printfn "  Metrics: Home OPS: %.3f, Away OPS: %.3f" 
                metrics.HomeOPS metrics.AwayOPS
        | None -> printfn "  Metrics: No data"
        
        printfn ""
    
    let printError (message: string) =
        Console.ForegroundColor <- ConsoleColor.Red
        printfn "%s" message
        Console.ResetColor()
    
    let loadGameData (filePath: string option) : Result<GameData list * string, string> =
        match filePath with
        | Some path ->
            printfn "Loading games from CSV file: %s" path
            
            match CsvParser.loadGamesFromCsv path with
            | CsvParser.LoadSuccess (games, errorCount) ->
                if errorCount > 0 then
                    printfn "Warning: %d lines failed to parse" errorCount
                printfn "Successfully loaded %d games from CSV" games.Length
                Ok (games, $"csv-file-{System.IO.Path.GetFileNameWithoutExtension(path)}")
            | CsvParser.FileNotFound msg ->
                Error msg
            | CsvParser.FileError msg ->
                Error msg
        | None ->
            Error "File path is required. Use -f or --file to specify a CSV file path."
    
    let generateEloPlusRatings (args: ParseResults<'T>) =
        printfn "=== Elo+ Rating Generation ==="
        
        let progressInterval = 10 // Default for now  
        let filePath = 
            try
                // Try to extract file path from args - using string matching for simplicity
                let argStrings = System.Environment.GetCommandLineArgs()
                let fileIndex = Array.tryFindIndex (fun s -> s = "--file" || s = "-f") argStrings
                match fileIndex with
                | Some i when i + 1 < argStrings.Length -> Some argStrings.[i + 1]
                | _ -> None
            with
            | _ -> None
        
        let markdownPath = 
            try
                let argStrings = System.Environment.GetCommandLineArgs()
                let markdownIndex = Array.tryFindIndex (fun s -> s = "--markdown" || s = "-m") argStrings
                match markdownIndex with
                | Some i when i + 1 < argStrings.Length -> Some argStrings.[i + 1]
                | _ -> None
            with
            | _ -> None
        
        // Load game data from file or use sample data
        match loadGameData filePath with
        | Error errorMsg ->
            printError errorMsg
            1
        | Ok (games, dataSource) ->
            if games.IsEmpty then
                printError "No valid games found in the data source."
                1
            else
                printfn "Processing %d games in chronological order..." games.Length
                printfn "Data source: %s" dataSource
                printfn "Progress reporting: every %d games" progressInterval
                printfn "Using K-Factor: %.0f, Default Rating: %.0f\n" 
                    EloCalculator.K_FACTOR EloCalculator.DEFAULT_RATING
                
                // Sort games chronologically and process with detailed output
                let sortedGames = games |> List.sortBy (fun g -> g.Date)
                let mutable currentRatings = Map.empty<string, decimal>
                
                printfn "Game-by-Game Elo Progression:"
                printfn "============================================"
                
                let startTime = DateTime.Now
                
                sortedGames
                |> List.iteri (fun i game ->
                    let gameDetails = EloCalculator.getGameDetails currentRatings game
                    if i % progressInterval = 0 then // Show every Nth game based on progress interval
                        printfn "%s" gameDetails
                    currentRatings <- EloCalculator.processGame currentRatings game
                )
                
                let endTime = DateTime.Now
                let processingTime = endTime - startTime
                
                // === ENHANCED ELO+ SYSTEM WITH PROPER METHODOLOGY ===
                printfn "\n=== Phase 1: Data Splitting ==="
                printfn "=============================="
                
                // Calculate final ratings (fallback approach)
                let finalRatings = EloCalculator.calculateEloRatings games
                let eloRatingRecords = EloCalculator.convertToEloRatings finalRatings DateTime.Now
                
                // Initialize with defaults
                let mutable vanillaEloRatings = Map.empty<string, float>
                let mutable vanillaMetrics = { 
                    EvaluationMetrics.TotalGames = 0; EvaluationMetrics.CorrectPredictions = 0; EvaluationMetrics.Accuracy = 0.0
                    EvaluationMetrics.LogLoss = 0.0; EvaluationMetrics.BrierScore = 0.0
                    EvaluationMetrics.TruePositives = 0; EvaluationMetrics.TrueNegatives = 0; EvaluationMetrics.FalsePositives = 0; EvaluationMetrics.FalseNegatives = 0
                    EvaluationMetrics.Precision = 0.0; EvaluationMetrics.Recall = 0.0; EvaluationMetrics.F1Score = 0.0; EvaluationMetrics.RocAuc = 0.5
                    EvaluationMetrics.CalibrationError = 0.0; EvaluationMetrics.IsWellCalibrated = false
                    EvaluationMetrics.AverageConfidence = 0.5; EvaluationMetrics.ConfidenceStdDev = 0.0
                    EvaluationMetrics.HomeTeamAdvantage = 0.5; EvaluationMetrics.ModelHomeBias = 0.0
                }
                let mutable eloPlusRatings = Map.empty<string, EloPlusCalculator.EloPlusRating>
                let mutable dataSplit = { 
                    DataSplitter.Training = []; DataSplitter.Validation = []; DataSplitter.Testing = []
                }
                let mutable features = []  // Initialize features list for markdown generation
                
                match DataSplitter.splitGames sortedGames with
                | Error errorMsg -> 
                    printError $"Data splitting failed: {errorMsg}"
                    printfn "Falling back to legacy approach...\n"
                    
                | Ok splitResult ->
                    dataSplit <- splitResult
                    printfn "%s" (DataSplitter.getDataSplitStatistics dataSplit)
                    
                    // === Phase 2: Vanilla Elo Baseline ===
                    printfn "\n=== Phase 2: Vanilla Elo Baseline ==="
                    printfn "===================================="
                    vanillaEloRatings <- VanillaEloCalculator.calculateVanillaEloRatings dataSplit.Training
                    let (vanillaReport, vanillaAccuracy) = VanillaEloCalculator.calculatePerformanceStatistics dataSplit.Testing VanillaEloCalculator.defaultConfig
                    printfn "%s" vanillaReport
                    
                    // === Phase 3: Elo+ Enhanced System ===
                    printfn "\n=== Phase 3: Elo+ Enhanced System ==="
                    printfn "===================================="
                    let (ratings, eloPlusReport) = EloPlusCalculator.calculateEloPlusRatingsWithSplitting games EloPlusCalculator.defaultConfig
                    eloPlusRatings <- ratings
                    printfn "%s" eloPlusReport
                
                printfn "\n=== Final Elo Ratings ==="
                printfn "========================="
                
                eloRatingRecords
                |> List.sortByDescending (fun r -> r.StandardElo)
                |> List.sortBy (fun r -> r.Team) // Secondary sort by team name for ties
                |> List.sortByDescending (fun r -> r.StandardElo) // Primary sort by rating
                |> List.iteri (fun i rating ->
                    printfn "%d. %-25s %.3f" (i + 1) rating.Team rating.StandardElo)
                
                printfn "\n=== Elo Rating Statistics ==="
                printfn "============================"
                
                let ratings = eloRatingRecords |> List.map (fun r -> r.StandardElo)
                let avgRating = ratings |> List.average
                let maxRating = ratings |> List.max
                let minRating = ratings |> List.min
                let ratingSpread = maxRating - minRating
                
                printfn "Average Rating: %.3f" avgRating
                printfn "Highest Rating: %.3f" maxRating
                printfn "Lowest Rating:  %.3f" minRating
                printfn "Rating Spread:  %.3f points" ratingSpread
                
                printfn "\n=== Team Performance Summary ==="
                printfn "==============================="
                
                let teamStats = 
                    games
                    |> List.collect (fun g -> 
                        let homeWin = g.HomeScore > g.AwayScore
                        let awayWin = g.AwayScore > g.HomeScore
                        [
                            (g.HomeTeam, if homeWin then (1, 0) elif awayWin then (0, 1) else (0, 0))
                            (g.AwayTeam, if awayWin then (1, 0) elif homeWin then (0, 1) else (0, 0))
                        ])
                    |> List.groupBy fst
                    |> List.map (fun (team, results) ->
                        let wins = results |> List.sumBy (fun (_, (w, _)) -> w)
                        let losses = results |> List.sumBy (fun (_, (_, l)) -> l)
                        let winPct = if (wins + losses) > 0 then float wins / float (wins + losses) else 0.0
                        (team, wins, losses, winPct))
                    |> List.sortByDescending (fun (_, _, _, pct) -> pct)
                
                for (team, wins, losses, winPct) in teamStats do
                    let finalRating = finalRatings.TryFind(team) |> Option.defaultValue EloCalculator.DEFAULT_RATING
                    printfn "%-25s %d-%d (%.3f) | Elo: %.3f" team wins losses winPct finalRating
                
                printfn "\n=== Processing Summary ==="
                printfn "========================="
                printfn "Games processed: %d" games.Length
                printfn "Processing time: %.2f seconds" processingTime.TotalSeconds
                printfn "Games per second: %.1f" (float games.Length / processingTime.TotalSeconds)
                printfn "Data source: %s" dataSource
                
                // === Phase 4: Comprehensive Evaluation ===
                printfn "\n=== Phase 4: Comprehensive Model Comparison ==="
                printfn "=============================================="
                
                // Create prediction functions for comparison
                let vanillaPredictFunc game = 
                    let homeRating = vanillaEloRatings.TryFind(game.HomeTeam) |> Option.defaultValue VanillaEloCalculator.DEFAULT_RATING
                    let awayRating = vanillaEloRatings.TryFind(game.AwayTeam) |> Option.defaultValue VanillaEloCalculator.DEFAULT_RATING
                    VanillaEloCalculator.calculateWinProbability homeRating awayRating VanillaEloCalculator.defaultConfig
                
                // Evaluate on test set (proper methodology) 
                if not dataSplit.Testing.IsEmpty then
                    let vanillaPredictions = EvaluationMetrics.createPredictionResults dataSplit.Testing vanillaPredictFunc
                    vanillaMetrics <- EvaluationMetrics.calculatePerformanceMetrics vanillaPredictions
                
                printfn "%s" (EvaluationMetrics.formatPerformanceMetrics vanillaMetrics "Vanilla Elo System")
                printfn "\nBaseline (Vanilla Elo): %.1f%% accuracy" (vanillaMetrics.Accuracy * 100.0)
                
                // Test feature engineering  
                printfn "\n=== ML Feature Engineering Analysis ==="
                printfn "====================================="
                features <- FeatureEngineering.convertGamesToFeatures dataSplit.Training
                let featureStats = FeatureEngineering.getFeatureStatistics features
                printfn "%s" featureStats
                
                if features.Length > 0 then
                    let firstFeature = features.[0]
                    printfn "\nSample Feature Vector (Game 1):"
                    printfn "HomeElo: %.1f, AwayElo: %.1f, EloDiff: %.1f" 
                        firstFeature.HomeElo firstFeature.AwayElo firstFeature.EloDifference
                    printfn "HomeERA+: %.2f, AwayERA+: %.2f, OPSDiff: %.3f" 
                        firstFeature.HomeERAAdvantage firstFeature.AwayERAAdvantage firstFeature.OPSDifferential
                    printfn "HomeWin: %b" firstFeature.HomeWin
                    
                    // Test ML.NET training (Phase 8 Step 3)
                    if features.Length >= 10 then // Need enough data for training/testing
                        printfn "\n=== ML.NET Model Training Test ==="
                        printfn "================================="
                        let baseline = vanillaMetrics.Accuracy
                        printfn "Baseline accuracy: %.1f%% (vanilla Elo predictions)" (baseline * 100.0)
                        
                        try
                            let result = MLModelTrainer.trainModel features MLModelTrainer.defaultConfig
                            printfn "%s" (MLModelTrainer.formatTrainingResult result)
                            
                            if result.Accuracy > baseline then
                                printfn "✅ ML model beats baseline by %.1f%%" ((result.Accuracy - baseline) * 100.0)
                            else
                                printfn "⚠️  ML model underperformed baseline"
                        with
                        | ex -> 
                            printfn "❌ ML training failed: %s" ex.Message
                    else
                        printfn "\n⚠️  Not enough data for ML training (need ≥10 games, have %d)" features.Length
                
                // === Phase 5: Mathematical Framework Demonstration ===
                printfn "\n=== Mathematical Framework Demonstration ==="
                printfn "=========================================="
                
                // Demonstrate different tilting models
                let demoEloProb = 0.60
                let demoMLProb = 0.75
                let demoConfidence = Some 0.85
                
                printfn "Example Prediction Combination:"
                printfn "  Elo Probability: %.1f%%" (demoEloProb * 100.0)
                printfn "  ML Probability: %.1f%%" (demoMLProb * 100.0)
                printfn "  ML Confidence: %.1f%%" (demoConfidence.Value * 100.0)
                printfn ""
                
                let linearResult = EloPlusCalculator.calculateEloPlusProbability (EloPlusCalculator.LinearCombination 0.3) demoEloProb demoMLProb demoConfidence
                let weightedResult = EloPlusCalculator.calculateEloPlusProbability (EloPlusCalculator.WeightedAverage (0.7, 0.3)) demoEloProb demoMLProb demoConfidence
                let confidenceResult = EloPlusCalculator.calculateEloPlusProbability (EloPlusCalculator.ConfidenceWeighted 0.4) demoEloProb demoMLProb demoConfidence
                
                printfn "Tilting Model Results:"
                printfn "  Linear Combination (α=0.3): %.1f%%" (linearResult * 100.0)
                printfn "  Weighted Average (0.7:0.3): %.1f%%" (weightedResult * 100.0)  
                printfn "  Confidence Weighted (α=0.4): %.1f%%" (confidenceResult * 100.0)
                
                // Display Elo+ ratings
                printfn "\n=== Final Elo+ Enhanced Ratings ==="
                printfn "=================================="
                let eloPlusStats = EloPlusCalculator.getEloPlusStatistics eloPlusRatings
                printfn "%s" eloPlusStats
                
                if not (Map.isEmpty eloPlusRatings) then
                    printfn "\nTop 10 Elo+ Ratings:"
                    let topRatings = 
                        eloPlusRatings
                        |> Map.toList
                        |> List.map snd
                        |> List.sortByDescending (fun r -> r.FinalEloPlus)
                        |> List.take (min 10 (Map.count eloPlusRatings))
                    
                    topRatings
                    |> List.mapi (fun i rating ->
                        let adjustment = 
                            if rating.EloPlusAdjustment > 0.0 then sprintf "+%.1f" rating.EloPlusAdjustment 
                            elif rating.EloPlusAdjustment < 0.0 then sprintf "%.1f" rating.EloPlusAdjustment
                            else "±0.0"
                        let confidence = 
                            rating.MLConfidence 
                            |> Option.map (fun c -> sprintf " (%.1f%% conf)" (c * 100.0)) 
                            |> Option.defaultValue ""
                        sprintf "%d. %-25s Elo: %.1f → Elo+: %.1f [%s]%s" 
                            (i + 1) rating.Team (float rating.StandardElo) (float rating.FinalEloPlus) adjustment confidence)
                    |> List.iter (printfn "%s")
                else
                    printfn "\n❌ No Elo+ ratings calculated - insufficient game data or ML model training failed"

                // Generate markdown report if requested
                match markdownPath with
                | Some mdPath ->
                    printfn "\n=== Generating Markdown Report ==="
                    printfn "==============================="
                    
                    let mlTrainingResult = 
                        if features.Length >= 10 then
                            try
                                Some (MLModelTrainer.trainModel features MLModelTrainer.defaultConfig)
                            with
                            | _ -> None
                        else None
                    
                    let reportData = {
                        MarkdownReportGenerator.ReportData.Games = games
                        MarkdownReportGenerator.ReportData.DataSource = dataSource
                        MarkdownReportGenerator.ReportData.ProcessingTime = processingTime
                        MarkdownReportGenerator.ReportData.EloRatings = finalRatings
                        MarkdownReportGenerator.ReportData.EloPlusRatings = eloPlusRatings
                        MarkdownReportGenerator.ReportData.FeatureStats = if features.Length > 0 then Some (FeatureEngineering.getFeatureStatistics features) else None
                        MarkdownReportGenerator.ReportData.MLTrainingResult = mlTrainingResult
                        MarkdownReportGenerator.ReportData.EloPlusStats = if not (Map.isEmpty eloPlusRatings) then Some (EloPlusCalculator.getEloPlusStatistics eloPlusRatings) else None
                        // Enhanced reporting data
                        MarkdownReportGenerator.ReportData.DataSplitStats = Some (DataSplitter.getDataSplitStatistics dataSplit)
                        MarkdownReportGenerator.ReportData.VanillaEloMetrics = Some vanillaMetrics
                        MarkdownReportGenerator.ReportData.OptimizationResults = Some "Hyperparameter optimization included in training pipeline"
                        MarkdownReportGenerator.ReportData.MathematicalExplanation = true
                    }
                    
                    let markdownContent = MarkdownReportGenerator.generateReport reportData
                    
                    match MarkdownReportGenerator.saveReportToFile mdPath markdownContent with
                    | Ok () -> printfn "✅ Markdown report saved to: %s" mdPath
                    | Error errorMsg -> 
                        printError errorMsg
                        printfn "❌ Failed to save markdown report"
                | None -> 
                    printfn "\n💡 Use -m or --markdown <path> to generate a markdown report"

                0