namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO
open Plotly.NET
open Plotly.NET.LayoutObjects
open Plotly.NET.TraceObjects
open SleeperScoreCalculator

module HtmlOutputGenerator =

    let generateSleeperScoreTable (scores: SleeperScore list) (outputPath: string) =
        let headers = ["Player"; "Pos"; "Team"; "Opp"; "Sleeper Score"]

        // Build each row as a list of strings
        let tableRows =
            scores |> List.map (fun s ->
                [
                    s.Player
                    s.Position
                    s.Team
                    s.Opponent
                    sprintf "%.1f" s.TotalScore
                ]
            )

        // Create color scale for total score
        let totalValues = scores |> List.map (fun s -> s.TotalScore)
        let minTotal = totalValues |> List.min
        let maxTotal = totalValues |> List.max

        let getCellColor (v: float) =
            if maxTotal = minTotal then "rgb(240, 240, 255)"
            else
                let ratio = (v - minTotal) / (maxTotal - minTotal)
                // Low score (ratio=0): Light blue rgb(200, 220, 255)
                // High score (ratio=1): Red rgb(255, 100, 100)
                let r = int (200.0 + ratio * 55.0)   // 200 -> 255
                let g = int (220.0 - ratio * 120.0)  // 220 -> 100
                let b = int (255.0 - ratio * 155.0)  // 255 -> 100
                sprintf "rgb(%d, %d, %d)" r g b

        // Create color for each cell (organized by rows)
        let whiteColorStr = "rgb(255, 255, 255)"
        let cellColorRows =
            tableRows |> List.mapi (fun idx row ->
                row |> List.mapi (fun colIdx _ ->
                    if colIdx = 4 then // Sleeper Score column (last column) gets heatmap
                        getCellColor totalValues.[idx]
                    else
                        whiteColorStr
                )
            )

        // According to Plotly.NET docs, we need to:
        // 1. Create colors row by row
        // 2. Transpose to column orientation
        // 3. Convert each column to Color.fromColors
        // 4. Then wrap all columns with Color.fromColors

        let cellcolor =
            cellColorRows
            |> Seq.map (fun row ->
                row
                |> Seq.map Color.fromString)
            |> Seq.transpose  // Transpose from rows to columns
            |> Seq.map Color.fromColors  // Convert each column list to Color
            |> Color.fromColors  // Wrap all columns

        printfn "Cell colors created successfully"

        let table =
            Chart.Table(
                headers,
                tableRows,
                UseDefaults = false,
                CellsMultiAlign = [
                    StyleParam.HorizontalAlign.Left      // Player
                    StyleParam.HorizontalAlign.Center    // Pos
                    StyleParam.HorizontalAlign.Center    // Team
                    StyleParam.HorizontalAlign.Center    // Opp
                    StyleParam.HorizontalAlign.Right     // Sleeper Score
                ],
                CellsFillColor = cellcolor
            )
            |> Chart.withTitle "NFL Fantasy Sleeper Scores"
            // |> Chart.withSize(Height = 1000)
            |> Chart.withConfigStyle(Responsive = true)
            |> Chart.withConfig(Config.init(
                ToImageButtonOptions = ConfigObjects.ToImageButtonOptions.init(
                    Format = StyleParam.ImageFormat.PNG,
                    Scale = 2.0  // 2x scale for higher resolution
                )))

        // Expand tilde in path
        let expandedPath =
            if outputPath.StartsWith("~") then
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), outputPath.Substring(2))
            else
                outputPath

        // Ensure directory exists
        if not (Directory.Exists(expandedPath)) then
            Directory.CreateDirectory(expandedPath) |> ignore

        // Generate filename with timestamp
        let timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss")
        let filename = sprintf "sleeper_scores_%s.html" timestamp
        let fullPath = Path.Combine(expandedPath, filename)

        // Save Plotly chart as HTML
        table |> Chart.saveHtml(fullPath)

        // Add custom CSS to make all containers 100% height
        let htmlContent = File.ReadAllText(fullPath)
        let modifiedHtml = htmlContent.Replace("<body>", "<body><style>html, body, .js-plotly-plot, .plot-container, .svg-container { height: 100% !important; margin: 0; }</style>")
        File.WriteAllText(fullPath, modifiedHtml)

        fullPath

    let generateSleeperHitsTable (hits: SleeperHit list) (outputPath: string) =
        let headers = ["Player"; "Pos"; "Team"; "Opp"; "Last Week"; "This Week"; "Delta"; "Hit"; "Sleeper Score"]

        // Build each row as a list of strings
        let tableRows =
            hits |> List.map (fun h ->
                [
                    h.Player
                    h.Position
                    h.Team
                    h.Opponent
                    sprintf "%.1f" h.FantPtsLastWeek
                    sprintf "%.1f" h.FantPtsThisWeek
                    sprintf "%.1f" h.FantPtsDelta
                    if h.IsHit then "✓" else ""
                    sprintf "%.1f" h.TotalScore
                ]
            )

        // Create color scale for total score
        let totalValues = hits |> List.map (fun h -> h.TotalScore)
        let minTotal = totalValues |> List.min
        let maxTotal = totalValues |> List.max

        let getCellColor (v: float) =
            if maxTotal = minTotal then "rgb(240, 240, 255)"
            else
                let ratio = (v - minTotal) / (maxTotal - minTotal)
                let r = int (200.0 + ratio * 55.0)
                let g = int (220.0 - ratio * 120.0)
                let b = int (255.0 - ratio * 155.0)
                sprintf "rgb(%d, %d, %d)" r g b

        // Create color for each cell (organized by rows)
        let whiteColorStr = "rgb(255, 255, 255)"
        let greenColorStr = "rgb(200, 255, 200)"
        let cellColorRows =
            tableRows |> List.mapi (fun idx row ->
                row |> List.mapi (fun colIdx _ ->
                    if colIdx = 8 then // Sleeper Score column gets heatmap
                        getCellColor totalValues.[idx]
                    elif colIdx = 7 && hits.[idx].IsHit then // Hit column
                        greenColorStr
                    else
                        whiteColorStr
                )
            )

        let cellcolor =
            cellColorRows
            |> Seq.map (fun row ->
                row
                |> Seq.map Color.fromString)
            |> Seq.transpose
            |> Seq.map Color.fromColors
            |> Color.fromColors

        printfn "Cell colors created successfully"

        let table =
            Chart.Table(
                headers,
                tableRows,
                UseDefaults = false,
                CellsMultiAlign = [
                    StyleParam.HorizontalAlign.Left      // Player
                    StyleParam.HorizontalAlign.Center    // Pos
                    StyleParam.HorizontalAlign.Center    // Team
                    StyleParam.HorizontalAlign.Center    // Opp
                    StyleParam.HorizontalAlign.Right     // Last Week
                    StyleParam.HorizontalAlign.Right     // This Week
                    StyleParam.HorizontalAlign.Right     // Delta
                    StyleParam.HorizontalAlign.Center    // Hit
                    StyleParam.HorizontalAlign.Right     // Sleeper Score
                ],
                CellsFillColor = cellcolor
            )
            |> Chart.withTitle "NFL Fantasy Sleeper Hits Analysis"
            |> Chart.withConfigStyle(Responsive = true)
            |> Chart.withConfig(Config.init(
                ToImageButtonOptions = ConfigObjects.ToImageButtonOptions.init(
                    Format = StyleParam.ImageFormat.PNG,
                    Scale = 2.0
                )))

        // Expand tilde in path
        let expandedPath =
            if outputPath.StartsWith("~") then
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), outputPath.Substring(2))
            else
                outputPath

        // Ensure directory exists
        if not (Directory.Exists(expandedPath)) then
            Directory.CreateDirectory(expandedPath) |> ignore

        // Generate filename with timestamp
        let timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss")
        let filename = sprintf "sleeper_hits_%s.html" timestamp
        let fullPath = Path.Combine(expandedPath, filename)

        // Save Plotly chart as HTML
        table |> Chart.saveHtml(fullPath)

        // Add custom CSS to make all containers 100% height
        let htmlContent = File.ReadAllText(fullPath)
        let modifiedHtml = htmlContent.Replace("<body>", "<body><style>html, body, .js-plotly-plot, .plot-container, .svg-container { height: 100% !important; margin: 0; }</style>")
        File.WriteAllText(fullPath, modifiedHtml)

        fullPath
