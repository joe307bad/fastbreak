namespace Fastbreak.Cli.Services

open Microsoft.ML
open Microsoft.ML.Data
open System


module MLModelTrainer =
    
    // Training configuration
    type TrainingConfig = {
        TrainTestSplitRatio: float  // 0.8 = 80% training, 20% testing
        RandomSeed: int
    }
    
    // Training results
    type TrainingResult = {
        Accuracy: double
        AUC: double
        F1Score: double
        Precision: double
        Recall: double
        LogLoss: double
        TrainingTimeMs: double
        TrainingSamples: int
        TestSamples: int
    }
    
    // Default training configuration
    let defaultConfig = {
        TrainTestSplitRatio = 0.8
        RandomSeed = 42
    }
    
    // Train model with temporal splitting (chronological order preserved)
    let trainModel (features: GameFeatures list) (config: TrainingConfig) : TrainingResult =
        let mlContext = MLContext(seed = Nullable(config.RandomSeed))
        
        // Temporal split - first 80% for training, last 20% for testing
        let totalCount = features.Length
        let trainCount = int (float totalCount * config.TrainTestSplitRatio)
        
        let trainData = features |> List.take trainCount
        let testData = features |> List.skip trainCount
        
        let trainDataView = mlContext.Data.LoadFromEnumerable(trainData)
        let testDataView = mlContext.Data.LoadFromEnumerable(testData)
        
        printfn "Training samples: %d, Test samples: %d" trainData.Length testData.Length
        
        // Define features for training
        let featureColumns = [|
            "HomeElo"; "AwayElo"; "EloDifference";
            "HomeERAAdvantage"; "AwayERAAdvantage"; "HomeWHIPAdvantage"; "AwayWHIPAdvantage";
            "HomeStrikeoutRate"; "AwayStrikeoutRate";
            "OPSDifferential"; "ERAPlusDifferential"; "FIPDifferential";
            "PitcherMatchupAdvantage"
        |]
        
        // Training pipeline (Logistic Regression for binary classification) 
        let trainer = mlContext.BinaryClassification.Trainers.LbfgsLogisticRegression(
            labelColumnName = "Label")
        let pipeline = 
            EstimatorChain()
                .Append(mlContext.Transforms.Concatenate("Features", featureColumns))
                .Append(trainer)
        
        let startTime = DateTime.Now
        let trainedModel = pipeline.Fit(trainDataView)
        let endTime = DateTime.Now
        let trainingTimeMs = (endTime - startTime).TotalMilliseconds
        
        // Make predictions on test set
        let predictions = trainedModel.Transform(testDataView)
        
        // Evaluate the model
        let metrics = mlContext.BinaryClassification.Evaluate(predictions, labelColumnName = "Label")
        
        {
            Accuracy = metrics.Accuracy
            AUC = metrics.AreaUnderRocCurve
            F1Score = metrics.F1Score
            Precision = metrics.PositivePrecision
            Recall = metrics.PositiveRecall
            LogLoss = metrics.LogLoss
            TrainingTimeMs = trainingTimeMs
            TrainingSamples = trainData.Length
            TestSamples = testData.Length
        }
    
    // Make a simple prediction using the trained model  
    let makePrediction (mlContext: MLContext) (model: ITransformer) (features: GameFeatures) : GamePrediction =
        let predictionEngine = mlContext.Model.CreatePredictionEngine<GameFeatures, GamePrediction>(model)
        predictionEngine.Predict(features)
    
    // Format training results for display
    let formatTrainingResult (result: TrainingResult) : string =
        sprintf "Training Results:\n- Accuracy: %.3f (%.1f%%)\n- AUC: %.3f\n- F1 Score: %.3f\n- Precision: %.3f\n- Recall: %.3f\n- Log Loss: %.3f\n- Training Time: %.1f ms\n- Train/Test: %d/%d samples"
            result.Accuracy (result.Accuracy * 100.0) result.AUC result.F1Score 
            result.Precision result.Recall result.LogLoss result.TrainingTimeMs 
            result.TrainingSamples result.TestSamples
    
    // Calculate baseline accuracy (what we get if we always predict the majority class)
    let calculateBaseline (features: GameFeatures list) : double =
        let homeWins = features |> List.sumBy (fun f -> if f.HomeWin then 1 else 0)
        let totalGames = features.Length
        max (float homeWins / float totalGames) (float (totalGames - homeWins) / float totalGames)
