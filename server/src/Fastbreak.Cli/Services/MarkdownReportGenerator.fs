namespace Fastbreak.Cli.Services

open System
open System.Text
open Fastbreak.Cli.Entities

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
    
    let generateReport (reportData: ReportData) =
        let sb = StringBuilder()
        
        // Generate sections in new order: Elo+ ratings first, then standard ratings, then analysis
        sb.Append(generateEloPlusSection reportData.EloPlusRatings reportData.EloPlusStats) |> ignore
        sb.Append(generateEloRatingsSection reportData.EloRatings) |> ignore
        sb.Append(generateEloStatsSection reportData.EloRatings) |> ignore
        sb.Append(generateTeamPerformanceSection reportData.Games reportData.EloRatings) |> ignore
        sb.Append(generateMLSection reportData.FeatureStats reportData.MLTrainingResult) |> ignore
        sb.Append(generateSystemConfigSection ()) |> ignore
        
        sb.AppendLine() |> ignore
        sb.Append(generateMetadata reportData) |> ignore
        sb.AppendLine($"*Report generated by [Fastbreak Elo+ System](https://github.com/joe307bad/fastbreak/tree/4905239625f89e5fc0ef3b3e2af95653cf1ce10d/server/src/Fastbreak.Cli) on {formatDateTime DateTime.Now}*") |> ignore
        
        sb.ToString()
    
    let saveReportToFile (filePath: string) (content: string) =
        try
            System.IO.File.WriteAllText(filePath, content)
            Ok ()
        with
        | ex -> Error $"Failed to write markdown report to {filePath}: {ex.Message}"