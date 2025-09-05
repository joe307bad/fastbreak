namespace Fastbreak.Research.Cli.Commands.GenerateEloPlus

open Microsoft.ML
open Fastbreak.Research.Cli.Commands.GenerateEloPlus
open System

module EloPlusCalculator =
    
    // Mathematical formulation for combining Elo and ML predictions
    // P_final = f(P_elo, P_ml, α) where α is the tilting parameter
    type TiltingModel = 
        | LinearCombination of alpha: float              // P_final = (1-α) × P_elo + α × P_ml
        | WeightedAverage of eloWeight: float * mlWeight: float    // P_final = (w_elo × P_elo + w_ml × P_ml) / (w_elo + w_ml)
        | ConfidenceWeighted of alpha: float             // P_final = P_elo + α × conf_ml × (P_ml - P_elo)
    
    // Configuration for Elo+ enhancement
    type EloPlusConfig = {
        MLModelPath: string option
        TiltingModel: TiltingModel  // Mathematical approach for combining predictions
        ConfidenceThreshold: float  // Only apply ML adjustments when confidence > threshold
        MaxAdjustment: float        // Maximum Elo adjustment (in points) - legacy parameter
        LearningRate: float         // How aggressively to apply ML adjustments - legacy parameter
    }
    
    // Calculate Elo+ probability using the specified tilting model
    // This is the core mathematical innovation of the Elo+ system
    let calculateEloPlusProbability (model: TiltingModel) (eloProb: float) (mlProb: float) (confidence: float option) : float =
        match model with
        | LinearCombination alpha ->
            // Linear interpolation between Elo and ML predictions
            // When α = 0: Pure Elo, When α = 1: Pure ML, When α = 0.5: Equal weight
            (1.0 - alpha) * eloProb + alpha * mlProb
            
        | WeightedAverage (eloWeight, mlWeight) ->
            // Weighted average approach allowing different trust levels
            let totalWeight = eloWeight + mlWeight
            if totalWeight = 0.0 then eloProb
            else (eloWeight * eloProb + mlWeight * mlProb) / totalWeight
            
        | ConfidenceWeighted alpha ->
            // Confidence-modulated adjustment - only tilt when ML model is confident
            match confidence with
            | Some conf -> eloProb + alpha * conf * (mlProb - eloProb)
            | None -> eloProb  // Fall back to pure Elo if no confidence available

    // Default configuration using Linear Combination with moderate ML influence
    let defaultConfig = {
        MLModelPath = None
        TiltingModel = LinearCombination 0.3  // Trust Elo 70%, ML 30%
        ConfidenceThreshold = 0.6   // 60% confidence threshold
        MaxAdjustment = 50.0        // Max ±50 Elo points adjustment (legacy)
        LearningRate = 0.3          // 30% of the ML confidence applied (legacy)
    }
    
    // Enhanced Elo rating with ML prediction
    type EloPlusRating = {
        Team: string
        StandardElo: decimal
        MLConfidence: float option
        EloPlusAdjustment: float
        FinalEloPlus: decimal
        LastUpdated: DateTime
    }
    
    // Calculate Elo+ adjustment based on ML model confidence
    let calculateEloPlusAdjustment (mlPrediction: GamePrediction option) (actualResult: bool) (config: EloPlusConfig) : float =
        match mlPrediction with
        | None -> 0.0  // No ML model, no adjustment
        | Some prediction ->
            // Convert prediction probability to confidence score
            let confidence = if prediction.HomeWin then prediction.HomeWinProbability else (1.0f - prediction.HomeWinProbability)
            
            // Only apply adjustment if confidence exceeds threshold
            if float confidence < config.ConfidenceThreshold then
                0.0
            else
                // Calculate the prediction accuracy bonus/penalty
                let predictionCorrect = prediction.HomeWin = actualResult
                let confidenceScore = float confidence - 0.5  // Normalize to [-0.5, 0.5]
                
                // Positive adjustment if ML was confident and correct, negative if confident and wrong
                let baseAdjustment = if predictionCorrect then confidenceScore else -confidenceScore
                
                // Scale by learning rate and max adjustment
                baseAdjustment * config.LearningRate * config.MaxAdjustment * 2.0
    
    // Process a single game with Elo+ enhancement
    let processGameEloPlus (mlContext: MLContext option) (model: ITransformer option) 
                          (currentRatings: Map<string, EloPlusRating>) (game: GameData) (config: EloPlusConfig) 
                          : Map<string, EloPlusRating> =
        
        // Get current standard Elo ratings for the traditional calculation
        let homeElo = currentRatings.TryFind(game.HomeTeam) |> Option.map (fun r -> r.StandardElo) |> Option.defaultValue EloCalculator.DEFAULT_RATING
        let awayElo = currentRatings.TryFind(game.AwayTeam) |> Option.map (fun r -> r.StandardElo) |> Option.defaultValue EloCalculator.DEFAULT_RATING
        
        // Traditional Elo calculation first
        let standardRatingsMap = Map [ game.HomeTeam, homeElo; game.AwayTeam, awayElo ]
        let updatedStandardRatings = EloCalculator.processGame standardRatingsMap game
        
        // Get ML prediction if model is available
        let mlPrediction = 
            match mlContext, model with
            | Some ctx, Some mdl ->
                try
                    let features = FeatureEngineering.convertToFeatures standardRatingsMap game
                    let prediction = MLModelTrainer.makePrediction ctx mdl features
                    Some prediction
                with
                | _ -> None  // If ML prediction fails, fall back to standard Elo
            | _ -> None
        
        // Calculate game result
        let homeWin = game.HomeScore > game.AwayScore
        let awayWin = game.AwayScore > game.HomeScore
        
        // Calculate Elo+ adjustments for both teams
        let homeMLAdjustment = calculateEloPlusAdjustment mlPrediction homeWin config
        let awayMLAdjustment = calculateEloPlusAdjustment mlPrediction awayWin config
        
        // Create updated Elo+ ratings
        let homeStandardElo = updatedStandardRatings.[game.HomeTeam]
        let awayStandardElo = updatedStandardRatings.[game.AwayTeam]
        let timestamp = DateTime.Now
        
        let homeEloPlusRating = {
            Team = game.HomeTeam
            StandardElo = homeStandardElo
            MLConfidence = mlPrediction |> Option.map (fun p -> float p.HomeWinProbability)
            EloPlusAdjustment = homeMLAdjustment
            FinalEloPlus = homeStandardElo + decimal homeMLAdjustment
            LastUpdated = timestamp
        }
        
        let awayEloPlusRating = {
            Team = game.AwayTeam
            StandardElo = awayStandardElo
            MLConfidence = mlPrediction |> Option.map (fun p -> float (1.0f - p.HomeWinProbability))
            EloPlusAdjustment = awayMLAdjustment
            FinalEloPlus = awayStandardElo + decimal awayMLAdjustment
            LastUpdated = timestamp
        }
        
        // Update the ratings map
        currentRatings
        |> Map.add game.HomeTeam homeEloPlusRating
        |> Map.add game.AwayTeam awayEloPlusRating
    
    // Calculate Elo+ ratings using proper train/validation/test methodology
    let calculateEloPlusRatingsWithSplitting (games: GameData list) (config: EloPlusConfig) : Map<string, EloPlusRating> * string =
        match DataSplitter.splitGames games with
        | Error msg -> (Map.empty, $"Data splitting failed: {msg}")
        | Ok split ->
            // Phase 1: Train baseline Elo on training data only
            let trainingEloRatings = VanillaEloCalculator.calculateVanillaEloRatings split.Training
            
            // Phase 2: Train ML model on training data only
            let mlContext, mlModel = 
                if split.Training.Length >= 10 then
                    try
                        let features = FeatureEngineering.convertGamesToFeatures split.Training
                        let mlCtx = MLContext(seed = Nullable(42))
                        let result = MLModelTrainer.trainModel features MLModelTrainer.defaultConfig
                        
                        // Create the actual ML.NET model pipeline
                        let featureColumns = [|
                            "HomeElo"; "AwayElo"; "EloDifference";
                            "HomeERAAdvantage"; "AwayERAAdvantage"; "HomeWHIPAdvantage"; "AwayWHIPAdvantage";
                            "HomeStrikeoutRate"; "AwayStrikeoutRate";
                            "OPSDifferential"; "ERAPlusDifferential"; "FIPDifferential";
                            "PitcherMatchupAdvantage"
                        |]
                        
                        let trainDataView = mlCtx.Data.LoadFromEnumerable(features)
                        let trainer = mlCtx.BinaryClassification.Trainers.LbfgsLogisticRegression(labelColumnName = "Label")
                        let pipeline = 
                            Microsoft.ML.Data.EstimatorChain()
                                .Append(mlCtx.Transforms.Concatenate("Features", featureColumns))
                                .Append(trainer)
                        let model = pipeline.Fit(trainDataView) :> ITransformer
                        
                        printfn "ML model trained for Elo+ calculation (Accuracy: %.1f%%)" (result.Accuracy * 100.0)
                        (Some mlCtx, Some model)
                    with
                    | ex ->
                        printfn "ML training failed, using standard Elo: %s" ex.Message
                        (None, None)
                else
                    printfn "Not enough data for ML model, using standard Elo"
                    (None, None)
            
            // Phase 3: Optimize tilting parameter on validation data
            let optimizedAlpha = 
                match HyperparameterOptimizer.optimizeAlphaParameter split.Training split.Validation HyperparameterOptimizer.defaultOptimizationConfig with
                | Ok result -> result.BestAlpha
                | Error _ -> 0.3  // Fall back to default
            
            // Phase 4: Calculate final Elo+ ratings using optimized parameters
            let optimizedConfig = { config with TiltingModel = LinearCombination optimizedAlpha }
            // Calculate Elo+ ratings with actual ML adjustments
            let standardEloRatings = VanillaEloCalculator.calculateVanillaEloRatings (split.Training @ split.Validation)
                
            // Initialize mutable ratings dictionary for tracking
            let mutableRatings = System.Collections.Generic.Dictionary<string, EloPlusRating>()
            for KeyValue(teamName, eloRating) in standardEloRatings do
                mutableRatings.[teamName] <- {
                    Team = teamName
                    StandardElo = decimal eloRating
                    MLConfidence = None
                    EloPlusAdjustment = 0.0
                    FinalEloPlus = decimal eloRating
                    LastUpdated = DateTime.Now
                }
            
            // Process validation games to calculate actual ML-based adjustments
            let validationGamesWithML = 
                match mlContext, mlModel with
                | Some ctx, Some model ->
                    split.Validation
                    |> List.choose (fun game ->
                        try
                            // Get current ratings for this game
                            let homeElo = mutableRatings.[game.HomeTeam].StandardElo
                            let awayElo = mutableRatings.[game.AwayTeam].StandardElo
                            
                            // Create feature vector for ML prediction
                            let ratingsMap = Map [game.HomeTeam, homeElo; game.AwayTeam, awayElo]
                            let features = FeatureEngineering.convertToFeatures ratingsMap game
                            
                            // Get actual ML prediction with confidence
                            let prediction = MLModelTrainer.makePrediction ctx model features
                            let mlProb = float prediction.HomeWinProbability
                            let mlConfidence = if prediction.HomeWin then mlProb else (1.0 - mlProb)
                            
                            // Calculate Elo probability for comparison
                            let eloProb = VanillaEloCalculator.calculateWinProbability (float homeElo) (float awayElo) VanillaEloCalculator.defaultConfig
                            
                            Some (game, eloProb, mlProb, mlConfidence)
                        with
                        | _ -> None)
                | _ -> []
            
            // Apply ML adjustments based on actual predictions
            let finalRatings =
                if validationGamesWithML.IsEmpty then
                    // No ML model available, return standard Elo as Elo+
                    standardEloRatings
                    |> Map.map (fun teamName eloRating -> {
                        Team = teamName
                        StandardElo = decimal eloRating
                        MLConfidence = None
                        EloPlusAdjustment = 0.0
                        FinalEloPlus = decimal eloRating
                        LastUpdated = DateTime.Now
                    })
                else
                    // Calculate team-specific adjustments based on ML performance
                    let teamAdjustments = System.Collections.Generic.Dictionary<string, float * float * int>()
                    
                    // Accumulate ML vs Elo differences for each team
                    for (game, eloProb, mlProb, mlConfidence) in validationGamesWithML do
                        let homeAdj = optimizedAlpha * mlConfidence * (mlProb - eloProb) * 50.0
                        let awayAdj = optimizedAlpha * mlConfidence * ((1.0 - mlProb) - (1.0 - eloProb)) * 50.0
                        
                        let (homeSum, homeConf, homeCount) = 
                            teamAdjustments.TryGetValue(game.HomeTeam) |> function
                            | (true, (sum, conf, count)) -> (sum, conf, count)
                            | (false, _) -> (0.0, 0.0, 0)
                        
                        let (awaySum, awayConf, awayCount) = 
                            teamAdjustments.TryGetValue(game.AwayTeam) |> function
                            | (true, (sum, conf, count)) -> (sum, conf, count)
                            | (false, _) -> (0.0, 0.0, 0)
                        
                        teamAdjustments.[game.HomeTeam] <- (homeSum + homeAdj, homeConf + mlConfidence, homeCount + 1)
                        teamAdjustments.[game.AwayTeam] <- (awaySum + awayAdj, awayConf + mlConfidence, awayCount + 1)
                    
                    // Apply averaged adjustments to each team
                    standardEloRatings
                    |> Map.map (fun teamName eloRating ->
                        match teamAdjustments.TryGetValue(teamName) with
                        | (true, (adjSum, confSum, count)) when count > 0 ->
                            let avgAdjustment = adjSum / float count
                            let avgConfidence = confSum / float count
                            {
                                Team = teamName
                                StandardElo = decimal eloRating
                                MLConfidence = Some avgConfidence
                                EloPlusAdjustment = avgAdjustment
                                FinalEloPlus = decimal eloRating + decimal avgAdjustment
                                LastUpdated = DateTime.Now
                            }
                        | _ ->
                            // No ML data for this team
                            {
                                Team = teamName
                                StandardElo = decimal eloRating
                                MLConfidence = None
                                EloPlusAdjustment = 0.0
                                FinalEloPlus = decimal eloRating
                                LastUpdated = DateTime.Now
                            })
            
            let report = sprintf """Elo+ Training Pipeline Results:
====================================
Data Split: %d training, %d validation, %d test games
Optimized α: %.3f
ML Model: %s
Final Ratings: %d teams""" split.Training.Length split.Validation.Length split.Testing.Length optimizedAlpha (if mlModel.IsSome then "Successfully trained" else "Training failed") (Map.count finalRatings)
            
            (finalRatings, report)

    // Legacy method for backward compatibility (will be deprecated)  
    let calculateEloPlusRatingsLegacy (games: GameData list) (config: EloPlusConfig) : Map<string, EloPlusRating> =
        // Train ML model if we have enough data
        let mlContext, trainedModel = 
            if games.Length >= 10 then
                try
                    let features = FeatureEngineering.convertGamesToFeatures games
                    let mlCtx = MLContext(seed = Nullable(42))
                    let result = MLModelTrainer.trainModel features MLModelTrainer.defaultConfig
                    
                    // For now, we'll retrain the model each time
                    // In production, you'd save/load the model
                    let featureColumns = [|
                        "HomeElo"; "AwayElo"; "EloDifference";
                        "HomeERAAdvantage"; "AwayERAAdvantage"; "HomeWHIPAdvantage"; "AwayWHIPAdvantage";
                        "HomeStrikeoutRate"; "AwayStrikeoutRate";
                        "OPSDifferential"; "ERAPlusDifferential"; "FIPDifferential";
                        "PitcherMatchupAdvantage"
                    |]
                    
                    let trainDataView = mlCtx.Data.LoadFromEnumerable(features)
                    let trainer = mlCtx.BinaryClassification.Trainers.LbfgsLogisticRegression(labelColumnName = "Label")
                    let pipeline = 
                        Microsoft.ML.Data.EstimatorChain()
                            .Append(mlCtx.Transforms.Concatenate("Features", featureColumns))
                            .Append(trainer)
                    let model = pipeline.Fit(trainDataView) :> ITransformer
                    
                    printfn "ML model trained for Elo+ calculation (Accuracy: %.1f%%)" (result.Accuracy * 100.0)
                    (Some mlCtx, Some model)
                with
                | ex ->
                    printfn "ML training failed, using standard Elo: %s" ex.Message
                    (None, None)
            else
                printfn "Not enough data for ML model, using standard Elo"
                (None, None)
        
        // Process games chronologically
        let sortedGames = games |> List.sortBy (fun g -> g.Date)
        let mutable currentRatings = Map.empty<string, EloPlusRating>
        
        sortedGames
        |> List.fold (fun ratings game ->
            processGameEloPlus mlContext trainedModel ratings game config) currentRatings
    
    // Maintain original method for backward compatibility  
    let calculateEloPlusRatings (games: GameData list) (config: EloPlusConfig) : Map<string, EloPlusRating> =
        calculateEloPlusRatingsLegacy games config
    
    // Convert EloPlusRating to standard EloRating for compatibility
    let convertToEloRating (eloPlusRating: EloPlusRating) : EloRating =
        {
            Team = eloPlusRating.Team
            StandardElo = eloPlusRating.StandardElo
            EloPlus = Some eloPlusRating.FinalEloPlus
            LastUpdated = eloPlusRating.LastUpdated
        }
    
    // Get detailed Elo+ statistics
    let getEloPlusStatistics (ratings: Map<string, EloPlusRating>) : string =
        let ratingsList = ratings |> Map.toList |> List.map snd
        
        if ratingsList.IsEmpty then
            "No Elo+ ratings available"
        else
            let standardElos = ratingsList |> List.map (fun r -> float r.StandardElo)
            let eloPlusRatings = ratingsList |> List.map (fun r -> float r.FinalEloPlus)
            let adjustments = ratingsList |> List.map (fun r -> r.EloPlusAdjustment)
            
            let avgStandardElo = standardElos |> List.average
            let avgEloPlus = eloPlusRatings |> List.average
            let avgAdjustment = adjustments |> List.average
            let maxAdjustment = adjustments |> List.max
            let minAdjustment = adjustments |> List.min
            
            let teamsWithML = ratingsList |> List.filter (fun r -> r.MLConfidence.IsSome) |> List.length
            
            sprintf "- Avg Standard Elo: %.1f\n- Avg Elo+: %.1f\n- Avg ML Adjustment: %.1f\n- Max Adjustment: %.1f\n- Min Adjustment: %.1f\n- Teams with ML data: %d/%d"
                avgStandardElo avgEloPlus avgAdjustment maxAdjustment minAdjustment teamsWithML ratingsList.Length