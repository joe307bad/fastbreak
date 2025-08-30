namespace Fastbreak.Cli.Services

open Fastbreak.Cli.Entities
open System

module EvaluationMetrics =
    
    // Comprehensive evaluation metrics for sports prediction systems
    // These metrics provide different perspectives on model performance
    
    // Individual prediction result
    type PredictionResult = {
        GameDate: DateTime
        HomeTeam: string
        AwayTeam: string
        PredictedHomeWin: bool
        PredictedHomeProbability: float
        ActualHomeWin: bool
        IsCorrect: bool
    }
    
    // Comprehensive performance metrics
    type PerformanceMetrics = {
        // Basic Accuracy Metrics
        TotalGames: int
        CorrectPredictions: int
        Accuracy: float                     // Percentage of correct predictions
        
        // Probabilistic Scoring Metrics  
        LogLoss: float                     // Cross-entropy loss (lower is better)
        BrierScore: float                  // Mean squared error of probabilities (lower is better)
        
        // Classification Performance
        TruePositives: int                 // Correctly predicted home wins
        TrueNegatives: int                 // Correctly predicted away wins  
        FalsePositives: int                // Incorrectly predicted home wins
        FalseNegatives: int                // Incorrectly predicted away wins
        
        // Derived Classification Metrics
        Precision: float                   // TP / (TP + FP) - accuracy of positive predictions
        Recall: float                      // TP / (TP + FN) - sensitivity, true positive rate
        F1Score: float                     // Harmonic mean of precision and recall
        
        // ROC Analysis
        RocAuc: float                      // Area under ROC curve (discrimination ability)
        
        // Calibration Analysis
        CalibrationError: float            // Mean difference between predicted and actual frequencies
        IsWellCalibrated: bool             // Whether model probabilities match actual frequencies
        
        // Confidence and Uncertainty
        AverageConfidence: float           // Mean predicted probability (should be ~0.5 for fair odds)
        ConfidenceStdDev: float           // Standard deviation of predicted probabilities
        
        // Performance by Context
        HomeTeamAdvantage: float          // Actual home win rate in dataset
        ModelHomeBias: float              // Model's bias toward predicting home wins
    }
    
    // Calculate ROC AUC using trapezoidal rule approximation  
    let calculateRocAuc (predictions: PredictionResult list) : float =
        // Sort predictions by probability (descending)
        let sortedPredictions = predictions |> List.sortByDescending (fun p -> p.PredictedHomeProbability)
        
        let positives = predictions |> List.filter (fun p -> p.ActualHomeWin) |> List.length
        let negatives = predictions.Length - positives
        
        if positives = 0 || negatives = 0 then
            0.5  // No discrimination possible
        else
            let mutable truePositiveRate = 0.0
            let mutable falsePositiveRate = 0.0
            let mutable auc = 0.0
            let mutable currentTP = 0
            let mutable currentFP = 0
            
            // Calculate AUC using trapezoidal approximation
            for prediction in sortedPredictions do
                if prediction.ActualHomeWin then
                    currentTP <- currentTP + 1
                else
                    currentFP <- currentFP + 1
                
                let newTPR = float currentTP / float positives
                let newFPR = float currentFP / float negatives
                
                // Add trapezoid area
                auc <- auc + (newFPR - falsePositiveRate) * (truePositiveRate + newTPR) / 2.0
                
                truePositiveRate <- newTPR
                falsePositiveRate <- newFPR
            
            auc

    // Calculate calibration error using reliability diagram approach
    let calculateCalibrationError (predictions: PredictionResult list) : float =
        // Bin predictions by probability ranges
        let bins = Array.create 10 (0, 0)  // (count, correct) for each decile
        
        for prediction in predictions do
            let binIndex = min 9 (int (prediction.PredictedHomeProbability * 10.0))
            let (count, correct) = bins.[binIndex]
            bins.[binIndex] <- (count + 1, if prediction.IsCorrect then correct + 1 else correct)
        
        // Calculate weighted calibration error
        let mutable totalError = 0.0
        let totalPredictions = float predictions.Length
        
        for i in 0..9 do
            let (count, correct) = bins.[i]
            if count > 0 then
                let binMidpoint = (float i + 0.5) / 10.0
                let actualAccuracy = float correct / float count
                let binWeight = float count / totalPredictions
                totalError <- totalError + binWeight * abs(binMidpoint - actualAccuracy)
        
        totalError

    // Calculate comprehensive performance metrics
    let calculatePerformanceMetrics (predictions: PredictionResult list) : PerformanceMetrics =
        if predictions.IsEmpty then
            // Return empty metrics for no predictions
            {
                TotalGames = 0; CorrectPredictions = 0; Accuracy = 0.0
                LogLoss = Double.PositiveInfinity; BrierScore = 1.0
                TruePositives = 0; TrueNegatives = 0; FalsePositives = 0; FalseNegatives = 0
                Precision = 0.0; Recall = 0.0; F1Score = 0.0; RocAuc = 0.5
                CalibrationError = 1.0; IsWellCalibrated = false
                AverageConfidence = 0.5; ConfidenceStdDev = 0.0
                HomeTeamAdvantage = 0.5; ModelHomeBias = 0.0
            }
        else
            let totalGames = predictions.Length
            let correctPredictions = predictions |> List.filter (fun p -> p.IsCorrect) |> List.length
            let accuracy = float correctPredictions / float totalGames
            
            // Confusion matrix components
            let truePositives = predictions |> List.filter (fun p -> p.PredictedHomeWin && p.ActualHomeWin) |> List.length
            let trueNegatives = predictions |> List.filter (fun p -> not p.PredictedHomeWin && not p.ActualHomeWin) |> List.length
            let falsePositives = predictions |> List.filter (fun p -> p.PredictedHomeWin && not p.ActualHomeWin) |> List.length
            let falseNegatives = predictions |> List.filter (fun p -> not p.PredictedHomeWin && p.ActualHomeWin) |> List.length
            
            // Probabilistic scoring metrics
            let logLoss = 
                predictions
                |> List.map (fun p ->
                    let clampedProb = max 1e-15 (min (1.0 - 1e-15) p.PredictedHomeProbability)
                    if p.ActualHomeWin then -Math.Log(clampedProb) else -Math.Log(1.0 - clampedProb))
                |> List.average
            
            let brierScore = 
                predictions
                |> List.map (fun p ->
                    let actualResult = if p.ActualHomeWin then 1.0 else 0.0
                    (p.PredictedHomeProbability - actualResult) * (p.PredictedHomeProbability - actualResult))
                |> List.average
            
            // Classification metrics
            let precision = if (truePositives + falsePositives) = 0 then 0.0 else float truePositives / float (truePositives + falsePositives)
            let recall = if (truePositives + falseNegatives) = 0 then 0.0 else float truePositives / float (truePositives + falseNegatives)
            let f1Score = if (precision + recall) = 0.0 then 0.0 else 2.0 * precision * recall / (precision + recall)
            
            // ROC AUC calculation (simplified - full implementation would use all probability thresholds)
            let rocAuc = calculateRocAuc predictions
            
            // Calibration analysis
            let calibrationError = calculateCalibrationError predictions
            let isWellCalibrated = calibrationError < 0.05  // Within 5% is considered well calibrated
            
            // Confidence statistics
            let probabilities = predictions |> List.map (fun p -> p.PredictedHomeProbability)
            let avgConfidence = probabilities |> List.average
            let confidenceVariance = probabilities |> List.map (fun p -> (p - avgConfidence) * (p - avgConfidence)) |> List.average
            let confidenceStdDev = Math.Sqrt(confidenceVariance)
            
            // Context analysis
            let actualHomeWins = predictions |> List.filter (fun p -> p.ActualHomeWin) |> List.length
            let homeTeamAdvantage = float actualHomeWins / float totalGames
            
            let predictedHomeWins = predictions |> List.filter (fun p -> p.PredictedHomeWin) |> List.length
            let modelHomeBias = (float predictedHomeWins / float totalGames) - homeTeamAdvantage
            
            {
                TotalGames = totalGames
                CorrectPredictions = correctPredictions  
                Accuracy = accuracy
                LogLoss = logLoss
                BrierScore = brierScore
                TruePositives = truePositives
                TrueNegatives = trueNegatives
                FalsePositives = falsePositives
                FalseNegatives = falseNegatives
                Precision = precision
                Recall = recall
                F1Score = f1Score
                RocAuc = rocAuc
                CalibrationError = calibrationError
                IsWellCalibrated = isWellCalibrated
                AverageConfidence = avgConfidence
                ConfidenceStdDev = confidenceStdDev
                HomeTeamAdvantage = homeTeamAdvantage
                ModelHomeBias = modelHomeBias
            }
    
    
    // Create prediction results from games and a prediction function
    let createPredictionResults (games: GameData list) (predictFunc: GameData -> float) : PredictionResult list =
        games
        |> List.map (fun game ->
            let homeProbability = predictFunc game
            let predictedHomeWin = homeProbability > 0.5
            let actualHomeWin = game.HomeScore > game.AwayScore
            
            {
                GameDate = game.Date
                HomeTeam = game.HomeTeam
                AwayTeam = game.AwayTeam
                PredictedHomeWin = predictedHomeWin
                PredictedHomeProbability = homeProbability
                ActualHomeWin = actualHomeWin
                IsCorrect = predictedHomeWin = actualHomeWin
            })
    
    // Format performance metrics for display
    let formatPerformanceMetrics (metrics: PerformanceMetrics) (systemName: string) : string =
        sprintf """%s Performance Report:
%s
Games Evaluated: %d
Overall Accuracy: %.1f%% (%d/%d correct)

Probabilistic Scoring:
• Log-Loss: %.4f (lower is better, perfect = 0.000)
• Brier Score: %.4f (lower is better, perfect = 0.000)

Classification Performance:
• Precision: %.3f (accuracy of home win predictions)
• Recall: %.3f (sensitivity to actual home wins)  
• F1-Score: %.3f (harmonic mean of precision/recall)

Model Quality:
• ROC AUC: %.3f (discrimination ability, random = 0.500)
• Calibration Error: %.3f (%s)
• Well Calibrated: %s

Confidence Analysis:
• Average Confidence: %.3f (balanced = 0.500)
• Confidence Std Dev: %.3f (higher = more varied predictions)

Context Analysis:
• Actual Home Advantage: %.1f%% (%d/%d home wins)
• Model Home Bias: %+.1f%% (%s)

Confusion Matrix:
             Predicted
           Home  Away
Actual Home %4d  %4d  (%.1f%% recall)
       Away %4d  %4d  (%.1f%% specificity)"""
            systemName
            (String.replicate systemName.Length "=")
            metrics.TotalGames
            (metrics.Accuracy * 100.0) metrics.CorrectPredictions metrics.TotalGames
            metrics.LogLoss
            metrics.BrierScore  
            metrics.Precision
            metrics.Recall
            metrics.F1Score
            metrics.RocAuc
            metrics.CalibrationError
            (if metrics.IsWellCalibrated then "well calibrated" else "poorly calibrated")
            (if metrics.IsWellCalibrated then "✓" else "✗")
            metrics.AverageConfidence
            metrics.ConfidenceStdDev
            (metrics.HomeTeamAdvantage * 100.0) 
            (int (metrics.HomeTeamAdvantage * float metrics.TotalGames)) metrics.TotalGames
            (metrics.ModelHomeBias * 100.0)
            (if metrics.ModelHomeBias > 0.02 then "over-predicts home wins"
             elif metrics.ModelHomeBias < -0.02 then "under-predicts home wins" 
             else "well balanced")
            metrics.TruePositives metrics.FalseNegatives (metrics.Recall * 100.0)
            metrics.FalsePositives metrics.TrueNegatives 
            (if (metrics.TrueNegatives + metrics.FalsePositives) > 0 
             then float metrics.TrueNegatives / float (metrics.TrueNegatives + metrics.FalsePositives) * 100.0 
             else 0.0)
    
    // Compare two sets of performance metrics
    let compareMetrics (baseline: PerformanceMetrics) (improved: PerformanceMetrics) (baselineName: string) (improvedName: string) : string =
        let accuracyImprovement = (improved.Accuracy - baseline.Accuracy) * 100.0
        let logLossImprovement = baseline.LogLoss - improved.LogLoss  // Improvement is reduction in loss
        let brierImprovement = baseline.BrierScore - improved.BrierScore
        let rocAucImprovement = improved.RocAuc - baseline.RocAuc
        let calibrationImprovement = baseline.CalibrationError - improved.CalibrationError
        
        sprintf """Performance Comparison: %s vs %s
%s
Accuracy: %.1f%% → %.1f%% (%+.1f%% %s)
Log-Loss: %.4f → %.4f (%+.4f %s)  
Brier Score: %.4f → %.4f (%+.4f %s)
ROC AUC: %.3f → %.3f (%+.3f %s)
Calibration Error: %.3f → %.3f (%+.3f %s)

Statistical Significance:
• Games: %d vs %d
• Improvement: %s (%+.1f%% accuracy)
• Status: %s"""
            baselineName improvedName
            (String.replicate (baselineName.Length + improvedName.Length + 5) "=")
            (baseline.Accuracy * 100.0) (improved.Accuracy * 100.0) accuracyImprovement
            (if accuracyImprovement > 0.0 then "improvement ✓" else "decline ✗")
            baseline.LogLoss improved.LogLoss logLossImprovement
            (if logLossImprovement > 0.0 then "improvement ✓" else "decline ✗")
            baseline.BrierScore improved.BrierScore brierImprovement
            (if brierImprovement > 0.0 then "improvement ✓" else "decline ✗")
            baseline.RocAuc improved.RocAuc rocAucImprovement
            (if rocAucImprovement > 0.0 then "improvement ✓" else "decline ✗")
            baseline.CalibrationError improved.CalibrationError calibrationImprovement
            (if calibrationImprovement > 0.0 then "improvement ✓" else "decline ✗")
            baseline.TotalGames improved.TotalGames
            (if accuracyImprovement > 0.0 then "BETTER" else "WORSE") accuracyImprovement
            (if accuracyImprovement > 1.0 then "SIGNIFICANT IMPROVEMENT ✓✓" 
             elif accuracyImprovement > 0.0 then "Minor improvement ✓"
             elif accuracyImprovement > -1.0 then "Minor decline ✗"
             else "SIGNIFICANT DECLINE ✗✗")
    
    // Calculate baseline performance (always predict majority class)
    let calculateBaselineAccuracy (games: GameData list) : float =
        if games.IsEmpty then 0.0
        else
            let homeWins = games |> List.filter (fun g -> g.HomeScore > g.AwayScore) |> List.length
            let awayWins = games.Length - homeWins
            float (max homeWins awayWins) / float games.Length