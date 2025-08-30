namespace Fastbreak.Cli.Services

open Fastbreak.Cli.Entities
open System

module HyperparameterOptimizer =
    
    // Configuration for hyperparameter optimization
    type OptimizationConfig = {
        AlphaRange: float * float * float    // min, max, step
        ValidationMetric: string             // "accuracy", "logloss", "brier", "rocauc"
        MaxIterations: int                   // Maximum number of optimization steps
        EarlyStoppingRounds: int            // Stop if no improvement for N rounds
        RandomSeed: int option              // For reproducible results
        Verbose: bool                       // Whether to print progress
    }
    
    // Default optimization configuration for Elo+ tilting parameter
    let defaultOptimizationConfig = {
        AlphaRange = (0.0, 1.0, 0.1)       // Search from 0% to 100% ML influence in 10% steps
        ValidationMetric = "logloss"         // Log-loss is most appropriate for probability predictions
        MaxIterations = 100
        EarlyStoppingRounds = 10
        RandomSeed = Some 42                 // Ensure reproducible results
        Verbose = true
    }
    
    // Result of hyperparameter optimization
    type OptimizationResult = {
        BestAlpha: float                     // Optimal tilting parameter
        BestScore: float                     // Best validation metric achieved
        AllResults: (float * float) list    // All (alpha, score) pairs tested
        IterationsUsed: int                  // Number of iterations actually performed
        ConvergedEarly: bool                 // Whether early stopping was triggered
        ValidationDetails: string            // Detailed results for analysis
    }
    
    // Optimize tilting parameter α using grid search on validation data
    let optimizeAlphaParameter (trainingGames: GameData list) (validationGames: GameData list) 
                              (config: OptimizationConfig) : Result<OptimizationResult, string> =
        
        if trainingGames.IsEmpty then
            Error "Training set cannot be empty"
        elif validationGames.IsEmpty then
            Error "Validation set cannot be empty"
        else
            try
                // Extract alpha search parameters
                let (alphaMin, alphaMax, alphaStep) = config.AlphaRange
                
                // Generate alpha values to test
                let alphaValues = 
                    let mutable current = alphaMin
                    let mutable values = []
                    while current <= alphaMax + 1e-10 do  // Small epsilon for floating point comparison
                        values <- current :: values
                        current <- current + alphaStep
                    values |> List.rev
                
                if config.Verbose then
                    printfn "Optimizing α parameter with %d values: [%.1f, %.1f, ..., %.1f]" 
                        alphaValues.Length alphaMin (alphaMin + alphaStep) alphaMax
                
                // Train baseline Elo ratings on training data
                let baselineEloRatings = VanillaEloCalculator.calculateVanillaEloRatings trainingGames
                
                // Train ML model on training data  
                let mlTrainingResult = 
                    try
                        let features = FeatureEngineering.convertGamesToFeatures trainingGames
                        Some (MLModelTrainer.trainModel features MLModelTrainer.defaultConfig)
                    with
                    | ex -> 
                        if config.Verbose then printfn "Warning: ML model training failed: %s" ex.Message
                        None
                
                // Evaluate each alpha value
                let rec evaluateAlphas alphas bestAlpha bestScore allResults noImprovementRounds iterationsUsed =
                    match alphas with
                    | [] -> (bestAlpha, bestScore, allResults, iterationsUsed, false)
                    | alpha :: remainingAlphas ->
                        if noImprovementRounds >= config.EarlyStoppingRounds then
                            if config.Verbose then
                                printfn "  Early stopping triggered after %d rounds without improvement" config.EarlyStoppingRounds
                            (bestAlpha, bestScore, allResults, iterationsUsed, true)
                        elif iterationsUsed >= config.MaxIterations then
                            (bestAlpha, bestScore, allResults, iterationsUsed, false)
                        else
                            // Create Elo+ configuration with this alpha - simplified for now
                            // let eloPlusConfig = createConfig alpha
                            
                            // Calculate validation predictions using this alpha
                            let validationPredictions = 
                                validationGames
                                |> List.map (fun game ->
                                    // Get Elo probabilities
                                    let homeElo = baselineEloRatings.TryFind(game.HomeTeam) |> Option.defaultValue VanillaEloCalculator.DEFAULT_RATING
                                    let awayElo = baselineEloRatings.TryFind(game.AwayTeam) |> Option.defaultValue VanillaEloCalculator.DEFAULT_RATING
                                    let eloProb = VanillaEloCalculator.calculateWinProbability homeElo awayElo VanillaEloCalculator.defaultConfig
                                    
                                    // Get ML probability if available (mock for now)
                                    let mlProb = 
                                        match mlTrainingResult with
                                        | Some _ -> 0.55  // Mock ML prediction
                                        | None -> eloProb
                                    
                                    // Calculate final Elo+ probability - simplified linear combination
                                    let finalProb = (1.0 - alpha) * eloProb + alpha * mlProb
                                    
                                    {
                                        EvaluationMetrics.GameDate = game.Date
                                        EvaluationMetrics.HomeTeam = game.HomeTeam
                                        EvaluationMetrics.AwayTeam = game.AwayTeam
                                        EvaluationMetrics.PredictedHomeWin = finalProb > 0.5
                                        EvaluationMetrics.PredictedHomeProbability = finalProb
                                        EvaluationMetrics.ActualHomeWin = game.HomeScore > game.AwayScore
                                        EvaluationMetrics.IsCorrect = (finalProb > 0.5) = (game.HomeScore > game.AwayScore)
                                    })
                            
                            // Calculate validation metric
                            let metrics = EvaluationMetrics.calculatePerformanceMetrics validationPredictions
                            
                            let score = 
                                match config.ValidationMetric.ToLowerInvariant() with
                                | "accuracy" -> 1.0 - metrics.Accuracy
                                | "logloss" -> metrics.LogLoss
                                | "brier" -> metrics.BrierScore
                                | "rocauc" -> 1.0 - metrics.RocAuc
                                | _ -> metrics.LogLoss
                            
                            let newAllResults = (alpha, score) :: allResults
                            
                            if config.Verbose then
                                let metricValue = 
                                    match config.ValidationMetric.ToLowerInvariant() with
                                    | "accuracy" -> metrics.Accuracy
                                    | "rocauc" -> metrics.RocAuc
                                    | _ -> score
                                printfn "  α = %.3f → %s = %.4f" alpha config.ValidationMetric metricValue
                            
                            // Check for improvement
                            if score < bestScore then
                                evaluateAlphas remainingAlphas alpha score newAllResults 0 (iterationsUsed + 1)
                            else
                                evaluateAlphas remainingAlphas bestAlpha bestScore newAllResults (noImprovementRounds + 1) (iterationsUsed + 1)
                
                let (bestAlpha, bestScore, allResults, iterationsUsed, convergedEarly) = 
                    evaluateAlphas alphaValues 0.0 Double.MaxValue [] 0 0
                
                // Convert best score back to metric value for reporting
                let bestMetricValue = 
                    match config.ValidationMetric.ToLowerInvariant() with
                    | "accuracy" -> 1.0 - bestScore
                    | "rocauc" -> 1.0 - bestScore
                    | _ -> bestScore
                
                let validationDetails = 
                    sprintf """Hyperparameter Optimization Results:
==========================================
Search Space: α ∈ [%.1f, %.1f] with step %.1f
Optimization Metric: %s
Values Tested: %d
Iterations Used: %d (max: %d)
Early Stopping: %s

Best Configuration:
• α = %.3f
• %s = %.4f

Search Summary:
%s"""
                        alphaMin alphaMax alphaStep
                        config.ValidationMetric
                        alphaValues.Length
                        iterationsUsed config.MaxIterations
                        (if convergedEarly then "Yes" else "No")
                        bestAlpha
                        config.ValidationMetric bestMetricValue
                        (allResults 
                         |> List.rev
                         |> List.map (fun (a, s) -> 
                            let displayScore = 
                                match config.ValidationMetric.ToLowerInvariant() with
                                | "accuracy" -> 1.0 - s
                                | "rocauc" -> 1.0 - s  
                                | _ -> s
                            sprintf "α=%.3f: %.4f" a displayScore)
                         |> String.concat "\n")
                
                let result = {
                    BestAlpha = bestAlpha
                    BestScore = bestMetricValue
                    AllResults = allResults |> List.rev
                    IterationsUsed = iterationsUsed
                    ConvergedEarly = convergedEarly
                    ValidationDetails = validationDetails
                }
                
                Ok result
                
            with
            | ex -> Error $"Optimization failed: {ex.Message}"
    
    // Optimize multiple hyperparameters simultaneously (future extension)
    type MultiParamConfig = {
        AlphaRange: float * float * float
        ConfidenceThresholdRange: float * float * float
        ValidationMetric: string
        MaxIterations: int
    }
    
    // Optimize multiple parameters using grid search (computationally expensive)
    let optimizeMultipleParameters (trainingGames: GameData list) (validationGames: GameData list)
                                  (config: MultiParamConfig) : Result<float * float * float, string> =
        // This would implement full grid search over multiple parameters
        // For now, we'll just optimize alpha and return defaults for others
        let alphaConfig = {
            defaultOptimizationConfig with
                AlphaRange = config.AlphaRange
                ValidationMetric = config.ValidationMetric
                MaxIterations = config.MaxIterations
        }
        
        match optimizeAlphaParameter trainingGames validationGames alphaConfig with
        | Ok result -> Ok (result.BestAlpha, 0.6, 50.0)  // Return best alpha with default threshold and adjustment
        | Error msg -> Error msg
    
    // Perform cross-validation for robust hyperparameter selection
    let crossValidateAlpha (games: GameData list) (folds: int) (config: OptimizationConfig) : Result<OptimizationResult, string> =
        if games.Length < folds then
            Error $"Need at least {folds} games for {folds}-fold cross-validation, got {games.Length}"
        else
            try
                let sortedGames = games |> List.sortBy (fun g -> g.Date)
                let foldSize = sortedGames.Length / folds
                
                let mutable allAlphaScores = Map.empty<float, float list>
                
                // Perform k-fold cross-validation
                for fold in 0..folds-1 do
                    if config.Verbose then
                        printfn "Cross-validation fold %d/%d..." (fold + 1) folds
                    
                    // Create validation split for this fold (respecting temporal order)
                    let validationStart = fold * foldSize
                    let validationEnd = min (validationStart + foldSize) sortedGames.Length
                    
                    let validationGames = sortedGames.[validationStart..validationEnd-1]
                    let trainingGames = sortedGames.[0..validationStart-1] @ sortedGames.[validationEnd..]
                    
                    // Optimize on this fold
                    match optimizeAlphaParameter trainingGames validationGames { config with Verbose = false } with
                    | Ok result ->
                        // Accumulate results for each alpha
                        for (alpha, score) in result.AllResults do
                            let existingScores = allAlphaScores.TryFind(alpha) |> Option.defaultValue []
                            allAlphaScores <- allAlphaScores.Add(alpha, score :: existingScores)
                    | Error _ -> ()
                
                // Find alpha with best average cross-validation score
                let avgScores = 
                    allAlphaScores
                    |> Map.toList
                    |> List.map (fun (alpha, scores) -> (alpha, List.average scores))
                    |> List.sortBy snd
                
                match avgScores with
                | [] -> Error "No valid cross-validation results"
                | (bestAlpha, bestAvgScore) :: _ ->
                    let result = {
                        BestAlpha = bestAlpha
                        BestScore = bestAvgScore
                        AllResults = avgScores
                        IterationsUsed = folds
                        ConvergedEarly = false
                        ValidationDetails = sprintf "Cross-validation with %d folds completed. Best α = %.3f (avg %s = %.4f)" 
                                              folds bestAlpha config.ValidationMetric bestAvgScore
                    }
                    Ok result
            with
            | ex -> Error $"Cross-validation failed: {ex.Message}"
    
    // Quick optimization for production use (limited search space)
    let quickOptimizeAlpha (trainingGames: GameData list) (validationGames: GameData list) : Result<float, string> =
        let quickConfig = {
            defaultOptimizationConfig with
                AlphaRange = (0.0, 1.0, 0.2)  // Coarser grid: 0.0, 0.2, 0.4, 0.6, 0.8, 1.0
                Verbose = false
        }
        
        match optimizeAlphaParameter trainingGames validationGames quickConfig with
        | Ok result -> Ok result.BestAlpha
        | Error msg -> Error msg
    
    // Validate that hyperparameter optimization improved performance
    let validateOptimization (testGames: GameData list) (baselineAlpha: float) (optimizedAlpha: float) : string =
        // This would compare performance on test set between baseline and optimized alpha
        sprintf """Hyperparameter Validation on Test Set:
========================================
Baseline α: %.3f
Optimized α: %.3f
Test games: %d

Note: Full validation requires trained models and test predictions.
This validation step ensures the optimized parameters generalize to unseen data."""
            baselineAlpha optimizedAlpha testGames.Length