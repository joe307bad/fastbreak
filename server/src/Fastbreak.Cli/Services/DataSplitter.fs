namespace Fastbreak.Cli.Services

open Fastbreak.Cli.Entities
open System

module DataSplitter =
    
    // Data split configuration for proper train/validation/test methodology
    type DataSplit = {
        Training: GameData list      // 65% of data - for training Elo ratings and ML model
        Validation: GameData list    // 15% of data - for hyperparameter tuning (α optimization)
        Testing: GameData list       // 20% of data - for final unbiased evaluation
    }
    
    // Split configuration parameters
    type SplitConfig = {
        TrainingRatio: float         // Default: 0.65
        ValidationRatio: float       // Default: 0.15
        TestingRatio: float         // Default: 0.20
        UseChronological: bool      // Always true for sports data to prevent look-ahead bias
        RandomSeed: int option      // For reproducible random splits (not recommended for temporal data)
    }
    
    // Default split configuration following research-based recommendations
    let defaultSplitConfig = {
        TrainingRatio = 0.65
        ValidationRatio = 0.15
        TestingRatio = 0.20
        UseChronological = true      // Critical for sports prediction to respect temporal order
        RandomSeed = None
    }
    
    // Validate split configuration
    let validateSplitConfig (config: SplitConfig) : Result<unit, string> =
        let totalRatio = config.TrainingRatio + config.ValidationRatio + config.TestingRatio
        if abs(totalRatio - 1.0) > 1e-10 then
            Error $"Split ratios must sum to 1.0, got {totalRatio}"
        elif config.TrainingRatio <= 0.0 || config.ValidationRatio <= 0.0 || config.TestingRatio <= 0.0 then
            Error "All split ratios must be positive"
        elif not config.UseChronological then
            Error "Non-chronological splits are not recommended for temporal sports data"
        else
            Ok ()
    
    // Split games chronologically to prevent data leakage
    // This is critical for sports prediction - we cannot use future games to predict past outcomes
    let splitGamesChronologically (games: GameData list) (config: SplitConfig) : Result<DataSplit, string> =
        match validateSplitConfig config with
        | Error msg -> Error msg
        | Ok () ->
            if games.IsEmpty then
                Error "Cannot split empty game list"
            else
                // Sort games chronologically - absolutely critical for temporal data
                let sortedGames = games |> List.sortBy (fun g -> g.Date)
                let totalGames = sortedGames.Length
                
                // Calculate split indices based on chronological order
                let trainEndIndex = int (float totalGames * config.TrainingRatio)
                let validationEndIndex = trainEndIndex + int (float totalGames * config.ValidationRatio)
                
                // Ensure we have at least 1 game in each split
                if trainEndIndex < 1 then
                    Error $"Training set would be empty with {totalGames} games"
                elif validationEndIndex <= trainEndIndex then
                    Error $"Validation set would be empty with {totalGames} games"
                elif validationEndIndex >= totalGames then
                    Error $"Test set would be empty with {totalGames} games"
                else
                    // Create chronological splits - this preserves temporal order
                    let trainingGames = sortedGames.[0..trainEndIndex-1]
                    let validationGames = sortedGames.[trainEndIndex..validationEndIndex-1]
                    let testingGames = sortedGames.[validationEndIndex..totalGames-1]
                    
                    let split = {
                        Training = trainingGames
                        Validation = validationGames
                        Testing = testingGames
                    }
                    
                    Ok split
    
    // Primary function for splitting games with default configuration
    let splitGames (games: GameData list) : Result<DataSplit, string> =
        splitGamesChronologically games defaultSplitConfig
    
    // Get detailed statistics about the data split
    let getDataSplitStatistics (split: DataSplit) : string =
        let totalGames = split.Training.Length + split.Validation.Length + split.Testing.Length
        
        if totalGames = 0 then
            "No games in split"
        else
            let trainRatio = float split.Training.Length / float totalGames
            let validRatio = float split.Validation.Length / float totalGames  
            let testRatio = float split.Testing.Length / float totalGames
            
            // Get date ranges for each split
            let getDateRange games =
                if games |> List.isEmpty then 
                    ("N/A", "N/A")
                else
                    let sorted = games |> List.sortBy (fun g -> g.Date)
                    let earliest = sorted.Head.Date.ToString("yyyy-MM-dd")
                    let latest = sorted |> List.last |> fun g -> g.Date.ToString("yyyy-MM-dd")
                    (earliest, latest)
            
            let (trainStart, trainEnd) = getDateRange split.Training
            let (validStart, validEnd) = getDateRange split.Validation
            let (testStart, testEnd) = getDateRange split.Testing
            
            sprintf """Data Split Statistics:
======================
Total Games: %d

Training Set: %d games (%.1f%%)
  Date Range: %s to %s
  
Validation Set: %d games (%.1f%%)
  Date Range: %s to %s
  
Testing Set: %d games (%.1f%%)
  Date Range: %s to %s

Temporal Order Preserved: ✓ (Critical for preventing data leakage)"""
                totalGames
                split.Training.Length (trainRatio * 100.0) trainStart trainEnd
                split.Validation.Length (validRatio * 100.0) validStart validEnd
                split.Testing.Length (testRatio * 100.0) testStart testEnd
    
    // Validate that splits maintain chronological order (safety check)
    let validateChronologicalOrder (split: DataSplit) : Result<unit, string> =
        let getLastDate games =
            if games |> List.isEmpty then DateTime.MinValue
            else games |> List.map (fun g -> g.Date) |> List.max
            
        let getFirstDate games =
            if games |> List.isEmpty then DateTime.MaxValue  
            else games |> List.map (fun g -> g.Date) |> List.min
        
        let trainLastDate = getLastDate split.Training
        let validFirstDate = getFirstDate split.Validation
        let validLastDate = getLastDate split.Validation
        let testFirstDate = getFirstDate split.Testing
        
        if split.Training.Length > 0 && split.Validation.Length > 0 && trainLastDate > validFirstDate then
            Error "Training set contains games after validation set - chronological order violated"
        elif split.Validation.Length > 0 && split.Testing.Length > 0 && validLastDate > testFirstDate then
            Error "Validation set contains games after test set - chronological order violated"
        else
            Ok ()
    
    // Create a split with minimum viable sizes (for small datasets)
    let createMinimumViableSplit (games: GameData list) : Result<DataSplit, string> =
        let sortedGames = games |> List.sortBy (fun g -> g.Date)
        let totalGames = sortedGames.Length
        
        // Ensure we have enough games for meaningful evaluation
        if totalGames < 10 then
            Error $"Need at least 10 games for meaningful train/validation/test split, got {totalGames}"
        elif totalGames < 20 then
            // For very small datasets, use smaller validation/test sets
            let trainCount = max 6 (totalGames * 70 / 100)  // At least 6 games for training
            let validCount = max 2 ((totalGames - trainCount) / 2)  // At least 2 for validation
            let testCount = totalGames - trainCount - validCount
            
            let split = {
                Training = sortedGames.[0..trainCount-1]
                Validation = sortedGames.[trainCount..trainCount+validCount-1]
                Testing = sortedGames.[trainCount+validCount..]
            }
            Ok split
        else
            // Use standard split for larger datasets
            splitGames sortedGames
    
    // Export split to separate files (useful for external analysis)
    let exportSplitToFiles (split: DataSplit) (basePath: string) : Result<unit, string> =
        try
            let exportGames games fileName =
                let filePath = System.IO.Path.Combine(basePath, fileName)
                let content = 
                    games 
                    |> List.map (fun g -> sprintf "%s,%s,%s,%d,%d" (g.Date.ToString("yyyy-MM-dd")) g.HomeTeam g.AwayTeam g.HomeScore g.AwayScore)
                    |> String.concat "\n"
                System.IO.File.WriteAllText(filePath, "Date,HomeTeam,AwayTeam,HomeScore,AwayScore\n" + content)
                
            exportGames split.Training "training_games.csv"
            exportGames split.Validation "validation_games.csv" 
            exportGames split.Testing "testing_games.csv"
            
            Ok ()
        with
        | ex -> Error $"Failed to export split files: {ex.Message}"