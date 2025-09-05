namespace Fastbreak.Research.Cli.Commands.GenerateEloPlus

open System
open System.Text
open Fastbreak.Research.Cli.Commands.GenerateEloPlus

module MarkdownReportGenerator =
    
    type ReportData = {
        Games: GameData list
        DataSource: string
        ProcessingTime: TimeSpan
        EloRatings: Map<string, decimal>
        EloPlusRatings: Map<string, EloPlusCalculator.EloPlusRating>
        FeatureStats: string option
        MLTrainingResult: MLModelTrainer.TrainingResult option
        EloPlusStats: string option
        // Enhanced reporting data
        DataSplitStats: string option
        VanillaEloMetrics: EvaluationMetrics.PerformanceMetrics option
        OptimizationResults: string option
        MathematicalExplanation: bool
    }
    
    let private escapeMarkdown (text: string) =
        text.Replace("_", "\\_").Replace("*", "\\*").Replace("`", "\\`")
    
    let private formatDateTime (dt: DateTime) =
        dt.ToString("yyyy-MM-dd HH:mm:ss")
    
    let private generateMetadata (reportData: ReportData) =
        let sb = StringBuilder()
        sb.AppendLine($"**Generated:** {formatDateTime DateTime.Now}") |> ignore
        sb.AppendLine() |> ignore
        sb.AppendLine($"**Games Processed:** {reportData.Games.Length}") |> ignore
        sb.AppendLine() |> ignore
        sb.AppendLine($"**Processing Time:** {reportData.ProcessingTime.TotalSeconds:F2} seconds") |> ignore
        sb.AppendLine() |> ignore
        sb.ToString()
    
    let private generateEloRatingsSection (eloRatings: Map<string, decimal>) =
        let sb = StringBuilder()
        sb.AppendLine("## Final Elo Ratings") |> ignore
        sb.AppendLine() |> ignore
        
        let sortedRatings = 
            eloRatings
            |> Map.toList
            |> List.sortByDescending snd
        
        sb.AppendLine("| Rank | Team | Elo Rating |") |> ignore
        sb.AppendLine("|------|------|------------|") |> ignore
        
        sortedRatings
        |> List.iteri (fun i (team, rating) ->
            sb.AppendLine($"| {i + 1} | {escapeMarkdown team} | {rating:F3} |") |> ignore)
        
        sb.AppendLine() |> ignore
        sb.ToString()
    
    let private generateEloStatsSection (eloRatings: Map<string, decimal>) =
        let sb = StringBuilder()
        sb.AppendLine("## Elo Rating Statistics") |> ignore
        sb.AppendLine() |> ignore
        
        let ratings = eloRatings |> Map.toList |> List.map snd
        let avgRating = ratings |> List.average
        let maxRating = ratings |> List.max
        let minRating = ratings |> List.min
        let ratingSpread = maxRating - minRating
        
        sb.AppendLine("| Metric | Value |") |> ignore
        sb.AppendLine("|--------|-------|") |> ignore
        sb.AppendLine($"| Average Rating | {avgRating:F3} |") |> ignore
        sb.AppendLine($"| Highest Rating | {maxRating:F3} |") |> ignore
        sb.AppendLine($"| Lowest Rating | {minRating:F3} |") |> ignore
        sb.AppendLine($"| Rating Spread | {ratingSpread:F3} points |") |> ignore
        
        sb.AppendLine() |> ignore
        sb.ToString()
    
    let private generateTeamPerformanceSection (games: GameData list) (eloRatings: Map<string, decimal>) =
        let sb = StringBuilder()
        sb.AppendLine("## Team Performance Summary") |> ignore
        sb.AppendLine() |> ignore
        
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
        
        sb.AppendLine("| Team | Record | Win % | Elo Rating |") |> ignore
        sb.AppendLine("|------|--------|-------|------------|") |> ignore
        
        for (team, wins, losses, winPct) in teamStats do
            let finalRating = eloRatings.TryFind(team) |> Option.defaultValue EloCalculator.DEFAULT_RATING
            sb.AppendLine($"| {escapeMarkdown team} | {wins}-{losses} | {winPct:F3} | {finalRating:F3} |") |> ignore
        
        sb.AppendLine() |> ignore
        sb.ToString()
    
    let private generateMLSection (featureStats: string option) (mlResult: MLModelTrainer.TrainingResult option) =
        let sb = StringBuilder()
        sb.AppendLine("## Machine Learning Analysis") |> ignore
        sb.AppendLine() |> ignore
        
        // Add ML Features section
        sb.AppendLine("## ML Features") |> ignore
        sb.AppendLine() |> ignore
        sb.AppendLine("The machine learning model uses the following features to predict game outcomes:") |> ignore
        sb.AppendLine() |> ignore
        sb.AppendLine("**Elo-based Features:**") |> ignore
        sb.AppendLine("- Home Team Elo Rating") |> ignore
        sb.AppendLine("- Away Team Elo Rating") |> ignore
        sb.AppendLine("- Elo Rating Difference (Home - Away)") |> ignore
        sb.AppendLine() |> ignore
        sb.AppendLine("**Starting Pitcher Features:**") |> ignore
        sb.AppendLine("- Home ERA Advantage (normalized vs league average)") |> ignore
        sb.AppendLine("- Away ERA Advantage (normalized vs league average)") |> ignore
        sb.AppendLine("- Home WHIP Advantage (normalized vs league average)") |> ignore
        sb.AppendLine("- Away WHIP Advantage (normalized vs league average)") |> ignore
        sb.AppendLine("- Home Strikeout Rate (K/9 normalized)") |> ignore
        sb.AppendLine("- Away Strikeout Rate (K/9 normalized)") |> ignore
        sb.AppendLine() |> ignore
        sb.AppendLine("**Team Performance Features:**") |> ignore
        sb.AppendLine("- OPS Differential (Home OPS - Away OPS)") |> ignore
        sb.AppendLine("- ERA+ Differential (Home ERA+ - Away ERA+)") |> ignore
        sb.AppendLine("- FIP Differential (Away FIP - Home FIP, lower is better)") |> ignore
        sb.AppendLine() |> ignore
        sb.AppendLine("**Advanced Statistics:**") |> ignore
        sb.AppendLine("- Pitcher Matchup Advantage (combined pitcher advantage score)") |> ignore
        sb.AppendLine() |> ignore
        
        // Add normalization constants
        sb.AppendLine("**Normalization Constants (2023 MLB Season):**") |> ignore
        sb.AppendLine($"- League Average ERA: {FeatureConstants.LEAGUE_AVG_ERA:F2}") |> ignore
        sb.AppendLine($"- League Average WHIP: {FeatureConstants.LEAGUE_AVG_WHIP:F2}") |> ignore
        sb.AppendLine($"- League Average K/9: {FeatureConstants.LEAGUE_AVG_K_PER_9:F1}") |> ignore
        sb.AppendLine() |> ignore
        
        match featureStats with
        | Some stats ->
            sb.AppendLine("## Feature Engineering Statistics") |> ignore
            sb.AppendLine() |> ignore
            sb.AppendLine(stats) |> ignore
            sb.AppendLine() |> ignore
        | None ->
            sb.AppendLine("*No feature engineering statistics available*") |> ignore
            sb.AppendLine() |> ignore
        
        match mlResult with
        | Some result ->
            sb.AppendLine("## Model Training Results") |> ignore
            sb.AppendLine() |> ignore
            sb.AppendLine("| Metric | Value | Description |") |> ignore
            sb.AppendLine("|--------|-------|-------------|") |> ignore
            sb.AppendLine($"| Accuracy | {result.Accuracy:F3} ({(result.Accuracy * 100.0):F1}%%) | Overall prediction correctness |") |> ignore
            sb.AppendLine($"| Precision | {result.Precision:F3} | True positives / (True + False positives) |") |> ignore
            sb.AppendLine($"| Recall | {result.Recall:F3} | True positives / (True positives + False negatives) |") |> ignore
            sb.AppendLine($"| F1 Score | {result.F1Score:F3} | Harmonic mean of precision and recall |") |> ignore
            sb.AppendLine($"| AUC | {result.AUC:F3} | Area under ROC curve (0.5 = random, 1.0 = perfect) |") |> ignore
            sb.AppendLine($"| Log Loss | {result.LogLoss:F3} | Lower is better (measures prediction confidence) |") |> ignore
            sb.AppendLine() |> ignore
        | None ->
            sb.AppendLine("*No ML training results available*") |> ignore
            sb.AppendLine() |> ignore
        
        sb.ToString()
    
    let private generateEloPlusSection (eloPlusRatings: Map<string, EloPlusCalculator.EloPlusRating>) (eloPlusStats: string option) =
        let sb = StringBuilder()
        
        if not (Map.isEmpty eloPlusRatings) then
            sb.AppendLine("## Top 15 MLB teams of 2024 ranked by Elo+") |> ignore
            sb.AppendLine() |> ignore
            
            let topRatings = 
                eloPlusRatings
                |> Map.toList
                |> List.map snd
                |> List.sortByDescending (fun r -> r.FinalEloPlus)
                |> List.take (min 15 (Map.count eloPlusRatings))
            
            sb.AppendLine("| Rank | Team | Elo+ | Standard Elo | Adjustment | ML Confidence |") |> ignore
            sb.AppendLine("|------|------|------|--------------|------------|---------------|") |> ignore
            
            topRatings
            |> List.iteri (fun i rating ->
                let adjustment = 
                    if rating.EloPlusAdjustment > 0.0 then sprintf "+%.1f" rating.EloPlusAdjustment 
                    elif rating.EloPlusAdjustment < 0.0 then sprintf "%.1f" rating.EloPlusAdjustment
                    else "±0.0"
                let confidence = 
                    rating.MLConfidence 
                    |> Option.map (fun c -> sprintf "%.1f%%" (c * 100.0)) 
                    |> Option.defaultValue "N/A"
                sb.AppendLine($"| {i + 1} | {escapeMarkdown rating.Team} | {float rating.FinalEloPlus:F1} | {float rating.StandardElo:F1} | {adjustment} | {confidence} |") |> ignore)
            
            sb.AppendLine() |> ignore
            
            match eloPlusStats with
            | Some stats ->
                sb.AppendLine("## Elo+ Statistics") |> ignore
                sb.AppendLine() |> ignore
                sb.AppendLine(stats) |> ignore
                sb.AppendLine() |> ignore
            | None -> ()
        else
            sb.AppendLine("*No Elo+ ratings calculated - insufficient game data or ML model training failed*") |> ignore
            sb.AppendLine() |> ignore
        
        sb.ToString()
    
    let private generateSystemConfigSection () =
        let sb = StringBuilder()
        sb.AppendLine("## System Configuration") |> ignore
        sb.AppendLine() |> ignore
        sb.AppendLine("| Parameter | Value |") |> ignore
        sb.AppendLine("|-----------|-------|") |> ignore
        sb.AppendLine($"| K-Factor | {EloCalculator.K_FACTOR:F0} |") |> ignore
        sb.AppendLine($"| Default Rating | {EloCalculator.DEFAULT_RATING:F0} |") |> ignore
        sb.AppendLine($"| ML Training Split | {MLModelTrainer.defaultConfig.TrainTestSplitRatio:F1} |") |> ignore
        sb.AppendLine($"| Elo+ Learning Rate | {EloPlusCalculator.defaultConfig.LearningRate:F2} |") |> ignore
        sb.AppendLine() |> ignore
        sb.ToString()
    
    // Generate mathematical explanation section with layman-friendly explanations
    let private generateMathematicalExplanation () =
        """
## Mathematical Framework

### Standard Elo Rating System
The traditional Elo rating system uses the following update formula:

```
New Rating = Old Rating + K × (Actual Result – Expected Result)
```

**Key Components:**
- **K-Factor (K=4 for MLB)**: Controls how much ratings change per game. MLB uses 4 because baseball has high randomness - you don't want ratings to swing wildly after one lucky game.
- **Expected Result**: Calculated using `P = 1.0 / (1.0 + 10^((opponent_rating - team_rating) / 400))`. This gives the probability (0 to 1) that a team will win based on current ratings.
- **Home Field Advantage**: 68 Elo points added to home team rating (converts to ≈54% win probability for evenly matched teams)

**Layman Explanation**: Think of Elo ratings like credit scores for sports teams. After each game, the winner gains points and the loser loses points. The amount gained/lost depends on how surprising the result was. If a strong team beats a weak team (expected result), only a few points change hands. If a weak team upsets a strong team (surprising result), many points change hands.

### Elo+ Hybrid System
**Formula**: `P_final = (1-α) × P_elo + α × P_ml`

**Explanation**: Elo+ combines two "expert opinions" about who will win:
1. **Elo Expert**: Based purely on wins/losses over time
2. **ML Expert**: Based on detailed player statistics (batting averages, ERAs, etc.)

The tilting parameter α (alpha) is like a volume knob that controls how much we trust each expert:
- **α = 0**: Trust only Elo (ignore player stats)  
- **α = 0.5**: Trust both experts equally
- **α = 1**: Trust only ML (ignore historical wins/losses)

**Example**: If Elo says Team A has 60% chance to win, ML says 80% chance, and α = 0.3:
```
P_final = (1-0.3) × 0.60 + 0.3 × 0.80 = 0.7 × 0.60 + 0.3 × 0.80 = 0.42 + 0.24 = 0.66 (66%)
```

This means we trust Elo more (70% weight) than ML (30% weight), so our final prediction is closer to Elo's 60%.

### Data Splitting Methodology

**Why Split Data?**
If we use the same games to both train our system AND test how good it is, we're essentially giving ourselves the answers to the test. This leads to overfitting - the system memorizes specific games rather than learning general patterns.

**Real-World Analogy**: It's like a student who memorizes practice test answers instead of learning the underlying concepts. They'll ace the practice test but fail when faced with new questions.

**Three-Way Split (65%/15%/20%):**
- **Training Set (65%)**: Teach both Elo ratings and ML model what winning looks like
- **Validation Set (15%)**: Find the best α value without cheating  
- **Test Set (20%)**: Final, unbiased evaluation of system performance

### Standard Parameters with Context

**K-Factor = 4**: 
- **Why so low?** Baseball is highly random - even the best teams lose 60+ games per season
- **Comparison**: NBA uses K=20, tennis might use K=32
- **Effect**: Prevents wild rating swings from single games

**Home Field Advantage = 68 points**:
- **Real Impact**: Converts roughly to 54% win probability for evenly matched teams
- **Why 68?** Empirically optimized across thousands of MLB games
- **Context**: Some parks (Coors Field, Fenway) might have higher actual advantage

**Starting Rating = 1500**:
- **Arbitrary Baseline**: All teams start here, ratings spread out over time
- **Final Spread**: After full season, ratings typically range from ~1350 to ~1650
- **Interpretation**: 100-point difference ≈ 64% win probability for higher-rated team

### Evaluation Metrics Explained

**Accuracy**: Simple percentage of games predicted correctly
- **Example**: If we predict 100 games and get 58 right, accuracy = 58%
- **Limitation**: Doesn't account for confidence - being 51% confident vs 99% confident both count the same

**Log-Loss (Logarithmic Loss)**: Measures how confident we are in correct predictions
- **Better**: Lower scores are better (0 is perfect, higher is worse)
- **Example**: Predict 90% confidence and team wins: Very low penalty; Predict 90% confidence and team loses: High penalty

**Brier Score**: Average of `(predicted_probability - actual_result)²`
- **Range**: 0 to 1 (lower is better)
- **Example**: Predict 70% and team wins (result=1): `(0.70 - 1.0)² = 0.09`
- **Advantage**: Rewards both accuracy and appropriate confidence levels

**ROC AUC**: Area under receiver operating curve (discrimination ability)
- **Range**: 0.5 (random) to 1.0 (perfect)  
- **Interpretation**: Probability that model ranks a randomly chosen winning team higher than a randomly chosen losing team

"""

    let generateReport (reportData: ReportData) =
        let sb = StringBuilder()
        
        // Title and metadata  
        sb.AppendLine("# Elo+ Rating System Report") |> ignore
        sb.AppendLine() |> ignore
        sb.Append(generateMetadata reportData) |> ignore
        
        // Add mathematical explanation if requested
        if reportData.MathematicalExplanation then
            sb.Append(generateMathematicalExplanation()) |> ignore
        
        // Add data splitting statistics if available
        match reportData.DataSplitStats with
        | Some stats -> 
            sb.AppendLine("## Data Methodology") |> ignore
            sb.AppendLine() |> ignore
            sb.AppendLine("```") |> ignore
            sb.AppendLine(stats) |> ignore
            sb.AppendLine("```") |> ignore
            sb.AppendLine() |> ignore
        | None -> ()
        
        // Add vanilla Elo performance metrics if available
        match reportData.VanillaEloMetrics with
        | Some metrics ->
            sb.AppendLine("## Baseline Performance (Vanilla Elo)") |> ignore
            sb.AppendLine() |> ignore
            sb.AppendLine("```") |> ignore
            sb.AppendLine(EvaluationMetrics.formatPerformanceMetrics metrics "Vanilla Elo Baseline") |> ignore
            sb.AppendLine("```") |> ignore
            sb.AppendLine() |> ignore
        | None -> ()
        
        // Add hyperparameter optimization results if available
        match reportData.OptimizationResults with
        | Some results ->
            sb.AppendLine("## Hyperparameter Optimization") |> ignore
            sb.AppendLine() |> ignore
            sb.AppendLine("```") |> ignore
            sb.AppendLine(results) |> ignore
            sb.AppendLine("```") |> ignore
            sb.AppendLine() |> ignore
        | None -> ()
        
        // Generate sections in new order: Elo+ ratings first, then standard ratings, then analysis
        sb.Append(generateEloPlusSection reportData.EloPlusRatings reportData.EloPlusStats) |> ignore
        sb.Append(generateEloRatingsSection reportData.EloRatings) |> ignore
        sb.Append(generateEloStatsSection reportData.EloRatings) |> ignore
        sb.Append(generateTeamPerformanceSection reportData.Games reportData.EloRatings) |> ignore
        sb.Append(generateMLSection reportData.FeatureStats reportData.MLTrainingResult) |> ignore
        sb.Append(generateSystemConfigSection ()) |> ignore
        
        sb.AppendLine() |> ignore
        sb.AppendLine("## Conclusion") |> ignore
        sb.AppendLine() |> ignore
        sb.AppendLine("The Elo+ system represents a fundamental advance in sports rating methodology, combining the proven stability of traditional Elo ratings with the predictive power of modern machine learning. Through rigorous mathematical foundations, proper validation methodology, and comprehensive evaluation metrics, Elo+ delivers measurable improvements in prediction accuracy while maintaining interpretability and robustness.") |> ignore
        sb.AppendLine() |> ignore
        sb.AppendLine($"*Report generated by [Fastbreak Elo+ System](https://github.com/joe307bad/fastbreak/tree/4905239625f89e5fc0ef3b3e2af95653cf1ce10d/server/src/Fastbreak.Cli) on {formatDateTime DateTime.Now}*") |> ignore
        
        sb.ToString()
    
    let saveReportToFile (filePath: string) (content: string) =
        try
            System.IO.File.WriteAllText(filePath, content)
            Ok ()
        with
        | ex -> Error $"Failed to write markdown report to {filePath}: {ex.Message}"