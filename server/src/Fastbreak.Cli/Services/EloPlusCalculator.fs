namespace Fastbreak.Cli.Services

open Microsoft.ML
open Fastbreak.Cli.Entities
open System

module EloPlusCalculator =
    
    // Configuration for Elo+ enhancement
    type EloPlusConfig = {
        MLModelPath: string option
        ConfidenceThreshold: float  // Only apply ML adjustments when confidence > threshold
        MaxAdjustment: float        // Maximum Elo adjustment (in points)
        LearningRate: float         // How aggressively to apply ML adjustments
    }
    
    // Default configuration
    let defaultConfig = {
        MLModelPath = None
        ConfidenceThreshold = 0.6   // 60% confidence threshold
        MaxAdjustment = 50.0        // Max ±50 Elo points adjustment
        LearningRate = 0.3          // 30% of the ML confidence applied
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
    
    // Calculate Elo+ ratings for all games with ML enhancement
    let calculateEloPlusRatings (games: GameData list) (config: EloPlusConfig) : Map<string, EloPlusRating> =
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