namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO
open Microsoft.ML
open DataTypes

module DataLoader =

    /// Parse a single CSV row into PlayerWeeklyStats
    let parseRow (headers: string[]) (values: string[]) : PlayerWeeklyStats option =
        try
            let getValue (name: string) =
                let index = headers |> Array.findIndex (fun h -> h = name)
                values.[index]

            let parseFloat (name: string) =
                let value = getValue name
                if String.IsNullOrWhiteSpace(value) || value.ToUpper() = "NA" || value.ToUpper() = "NAN" then 0.0f
                else
                    match System.Single.TryParse(value) with
                    | (true, v) -> v
                    | (false, _) -> 0.0f

            let parseInt (name: string) =
                let value = getValue name
                if String.IsNullOrWhiteSpace(value) || value.ToUpper() = "NA" then 0
                else
                    match System.Int32.TryParse(value) with
                    | (true, v) -> v
                    | (false, _) -> 0

            let parseBool (name: string) =
                let value = getValue name
                if String.IsNullOrWhiteSpace(value) || value.ToUpper() = "NA" then false
                else value = "1" || value.ToUpper() = "TRUE"

            let getString (name: string) =
                let value = getValue name
                if String.IsNullOrWhiteSpace(value) then ""
                else value

            Some {
                PlayerId = getString "player_id"
                Player = getString "player"
                Position = getString "position"
                Team = getString "team"
                Opponent = getString "opponent"
                Hit = parseBool "hit"
                PrevWeekFp = parseFloat "prev_week_fp"
                CurrentWeekFp = parseFloat "current_week_fp"
                SleeperScore = parseFloat "sleeper_score"
                FpWeekDelta = parseFloat "fp_week_delta"
                TotalFpY2 = parseFloat "total_fp_y2"
                AvgFpY2 = parseFloat "avg_fp_y2"
                MaxFpY2 = parseFloat "max_fp_y2"
                MinFpY2 = parseFloat "min_fp_y2"
                FpPerSnapY2 = parseFloat "fp_per_snap_y2"
                FpConsistencyY2 = parseFloat "fp_consistency_y2"
                TotalFantasyPointsY1 = parseFloat "total_fantasy_points_y1"
                PpgY1 = parseFloat "ppg_y1"
                FpPerSnapY1 = parseFloat "fp_per_snap_y1"
                W1SnapShare = parseFloat "w1_snap_share"
                Y2SnapShareChange = parseFloat "y2_snap_share_change"
                SlidingWindowAvgDelta = parseFloat "sliding_window_avg_delta"
                MaxSnapPctY2 = parseFloat "max_snap_pct_y2"
                MinSnapPctY2 = parseFloat "min_snap_pct_y2"
                AvgSnapPctY2 = parseFloat "avg_snap_pct_y2"
                SnapPctChange = parseFloat "snap_pct_change"
                SnapPctVariance = parseFloat "snap_pct_variance"
                SnapConsistencyY2 = parseFloat "snap_consistency_y2"
                TotalOffSnapsY2 = parseFloat "total_off_snaps_y2"
                TotalOffSnapsY1 = parseFloat "total_off_snaps_y1"
                AvgSnapPctY1 = parseFloat "avg_snap_pct_y1"
                Crossed10PctSnaps = parseBool "crossed_10pct_snaps"
                Crossed20PctSnaps = parseBool "crossed_20pct_snaps"
                Crossed30PctSnaps = parseBool "crossed_30pct_snaps"
                HasPositiveTrend = parseBool "has_positive_trend"
                SignificantSnapJump = parseBool "significant_snap_jump"
                Height = parseFloat "height"
                Weight = parseFloat "weight"
                Age = parseFloat "age"
                RbSizeScore = parseFloat "rb_size_score"
                WrHeightScore = parseFloat "wr_height_score"
                TeSizeScore = parseFloat "te_size_score"
                DraftNumber = parseFloat "draft_number"
                EntryYear = parseInt "entry_year"
                YearsExp = parseInt "years_exp"
                College = getString "college"
                IsUdfa = parseBool "is_udfa"
                IsDay3Pick = parseBool "is_day3_pick"
                IsEarlyPick = parseBool "is_early_pick"
                IsYoungBreakout = parseBool "is_young_breakout"
                GamesY1 = parseFloat "games_y1"
                RookieYearUsage = parseFloat "rookie_year_usage"
                OpponentRushDefRank = parseFloat "opponent_rush_def_rank"
                OpponentPassDefRank = parseFloat "opponent_pass_def_rank"
                Ecr = parseFloat "ecr"
                PlayerAvailable = parseFloat "player_available"
                DraftValueScore = parseFloat "draft_value_score"
                PerformanceScore = parseFloat "performance_score"
                AgeScore = parseFloat "age_score"
                EcrScore = parseFloat "ecr_score"
                MatchupScore = parseFloat "matchup_score"
                SnapTrendScore = parseFloat "snap_trend_score"
                Season = parseInt "season"
                AnalysisWeek = parseInt "analysis_week"
                TotalGamesY2 = parseFloat "total_games_y2"
                GamesPlayedY2 = parseFloat "games_played_y2"
            }
        with ex ->
            printfn "Error parsing row: %s" ex.Message
            None

    /// Load a single CSV file
    let private loadCsvFile (filePath: string) : PlayerWeeklyStats list =
        try
            let lines = File.ReadAllLines(filePath)
            if lines.Length < 2 then
                printfn "Warning: %s has no data rows" (Path.GetFileName(filePath))
                []
            else
                let headers = lines.[0].Split(',')
                lines.[1..]
                |> Array.map (fun line -> line.Split(','))
                |> Array.choose (fun values -> parseRow headers values)
                |> Array.toList
        with ex ->
            printfn "Error loading file %s: %s" filePath ex.Message
            []

    /// Load all CSV files from the data folder
    let loadAllData (dataFolder: string) : PlayerWeeklyStats list =
        printfn "Loading data from: %s" dataFolder

        let csvFiles = Directory.GetFiles(dataFolder, "*.csv")
        printfn "Found %d CSV files" csvFiles.Length

        let allData =
            csvFiles
            |> Array.collect (fun file ->
                printfn "  Loading %s..." (Path.GetFileName(file))
                let data = loadCsvFile file
                printfn "    Loaded %d rows" data.Length
                data |> List.toArray)
            |> Array.toList

        printfn "Total rows loaded: %d" allData.Length
        allData

    /// Convert PlayerWeeklyStats to ModelInput for ML.NET
    let toModelInput (stats: PlayerWeeklyStats) : ModelInput =
        {
            PrevWeekFp = stats.PrevWeekFp
            CurrentWeekFp = stats.CurrentWeekFp
            SleeperScore = stats.SleeperScore
            FpWeekDelta = stats.FpWeekDelta
            TotalFpY2 = stats.TotalFpY2
            AvgFpY2 = stats.AvgFpY2
            MaxFpY2 = stats.MaxFpY2
            MinFpY2 = stats.MinFpY2
            FpPerSnapY2 = stats.FpPerSnapY2
            FpConsistencyY2 = stats.FpConsistencyY2
            TotalFantasyPointsY1 = stats.TotalFantasyPointsY1
            PpgY1 = stats.PpgY1
            FpPerSnapY1 = stats.FpPerSnapY1
            W1SnapShare = stats.W1SnapShare
            Y2SnapShareChange = stats.Y2SnapShareChange
            SlidingWindowAvgDelta = stats.SlidingWindowAvgDelta
            MaxSnapPctY2 = stats.MaxSnapPctY2
            MinSnapPctY2 = stats.MinSnapPctY2
            AvgSnapPctY2 = stats.AvgSnapPctY2
            SnapPctChange = stats.SnapPctChange
            SnapPctVariance = stats.SnapPctVariance
            SnapConsistencyY2 = stats.SnapConsistencyY2
            TotalOffSnapsY2 = stats.TotalOffSnapsY2
            TotalOffSnapsY1 = stats.TotalOffSnapsY1
            AvgSnapPctY1 = stats.AvgSnapPctY1
            Crossed10PctSnaps = stats.Crossed10PctSnaps
            Crossed20PctSnaps = stats.Crossed20PctSnaps
            Crossed30PctSnaps = stats.Crossed30PctSnaps
            HasPositiveTrend = stats.HasPositiveTrend
            SignificantSnapJump = stats.SignificantSnapJump
            Height = stats.Height
            Weight = stats.Weight
            Age = stats.Age
            RbSizeScore = stats.RbSizeScore
            WrHeightScore = stats.WrHeightScore
            TeSizeScore = stats.TeSizeScore
            DraftNumber = stats.DraftNumber
            YearsExp = float32 stats.YearsExp
            IsUdfa = stats.IsUdfa
            IsDay3Pick = stats.IsDay3Pick
            IsEarlyPick = stats.IsEarlyPick
            IsYoungBreakout = stats.IsYoungBreakout
            GamesY1 = stats.GamesY1
            RookieYearUsage = stats.RookieYearUsage
            OpponentRushDefRank = stats.OpponentRushDefRank
            OpponentPassDefRank = stats.OpponentPassDefRank
            Ecr = stats.Ecr
            PlayerAvailable = stats.PlayerAvailable
            DraftValueScore = stats.DraftValueScore
            PerformanceScore = stats.PerformanceScore
            AgeScore = stats.AgeScore
            EcrScore = stats.EcrScore
            MatchupScore = stats.MatchupScore
            SnapTrendScore = stats.SnapTrendScore
            TotalGamesY2 = stats.TotalGamesY2
            GamesPlayedY2 = stats.GamesPlayedY2
            Position = stats.Position
            Team = stats.Team
            Opponent = stats.Opponent
            Label = stats.Hit
        }

    /// Load data and convert to ML.NET IDataView
    let loadDataAsIDataView (mlContext: MLContext) (dataFolder: string) : Microsoft.ML.IDataView =
        let data = loadAllData dataFolder
        let modelInputs = data |> List.map toModelInput
        mlContext.Data.LoadFromEnumerable(modelInputs)

    /// Calculate dataset statistics
    let calculateStats (data: PlayerWeeklyStats list) (trainSize: int) (testSize: int) : DatasetStats =
        let hitCount = data |> List.filter (fun d -> d.Hit) |> List.length
        let nonHitCount = data.Length - hitCount
        {
            TotalSamples = data.Length
            TrainingSamples = trainSize
            TestSamples = testSize
            HitCount = hitCount
            NonHitCount = nonHitCount
            HitRate = float hitCount / float data.Length
            FeatureCount = 60 // Approximate count of features
        }
