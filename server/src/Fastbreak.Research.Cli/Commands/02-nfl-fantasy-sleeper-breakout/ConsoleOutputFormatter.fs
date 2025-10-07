namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open SleeperScoreCalculator

module ConsoleOutputFormatter =

    let printSleeperScores (scores: SleeperScore list) =
        printfn "%-30s %-4s %-4s %-4s %5s %5s %5s %5s %5s %5s %6s"
            "Player" "Pos" "Team" "Opp" "Draft" "Perf" "Age" "ECR" "Def" "Snap" "Total"
        printfn "%s" (String.replicate 100 "-")

        scores
        |> List.iter (fun score ->
            printfn "%-30s %-4s %-4s %-4s %5.0f %5.0f %5.0f %5.1f %5.0f %5.0f %6.1f"
                score.Player
                score.Position
                score.Team
                score.Opponent
                score.DraftValueScore
                score.PerformanceScore
                score.AgeScore
                score.EcrScore
                score.DefenseMatchupScore
                score.SnapTrendScore
                score.TotalScore)
