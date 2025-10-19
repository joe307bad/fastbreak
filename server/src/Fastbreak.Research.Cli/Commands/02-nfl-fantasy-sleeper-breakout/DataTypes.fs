namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System

module DataTypes =

    /// Player weekly stats record matching CSV schema
    type PlayerWeeklyStats = {
        // Identifiers
        PlayerId: string
        Player: string
        Position: string
        Team: string
        Opponent: string

        // Target variable
        Hit: bool

        // Performance metrics
        PrevWeekFp: float32
        CurrentWeekFp: float32
        SleeperScore: float32
        FpWeekDelta: float32

        // Year 2 fantasy stats
        TotalFpY2: float32
        AvgFpY2: float32
        MaxFpY2: float32
        MinFpY2: float32
        FpPerSnapY2: float32
        FpConsistencyY2: float32

        // Year 1 fantasy stats
        TotalFantasyPointsY1: float32
        PpgY1: float32
        FpPerSnapY1: float32

        // Snap count features
        W1SnapShare: float32
        Y2SnapShareChange: float32
        SlidingWindowAvgDelta: float32
        MaxSnapPctY2: float32
        MinSnapPctY2: float32
        AvgSnapPctY2: float32
        SnapPctChange: float32
        SnapPctVariance: float32
        SnapConsistencyY2: float32
        TotalOffSnapsY2: float32
        TotalOffSnapsY1: float32
        AvgSnapPctY1: float32

        // Snap threshold flags
        Crossed10PctSnaps: bool
        Crossed20PctSnaps: bool
        Crossed30PctSnaps: bool
        HasPositiveTrend: bool
        SignificantSnapJump: bool

        // Physical attributes
        Height: float32
        Weight: float32
        Age: float32

        // Position-specific scores
        RbSizeScore: float32
        WrHeightScore: float32
        TeSizeScore: float32

        // Draft/prospect info
        DraftNumber: float32
        EntryYear: int
        YearsExp: int
        College: string

        // Draft flags
        IsUdfa: bool
        IsDay3Pick: bool
        IsEarlyPick: bool
        IsYoungBreakout: bool

        // Rookie year context
        GamesY1: float32
        RookieYearUsage: float32

        // Matchup data
        OpponentRushDefRank: float32
        OpponentPassDefRank: float32

        // Expert consensus
        Ecr: float32
        PlayerAvailable: float32

        // Pre-calculated scores
        DraftValueScore: float32
        PerformanceScore: float32
        AgeScore: float32
        EcrScore: float32
        MatchupScore: float32
        SnapTrendScore: float32

        // Metadata
        Season: int
        AnalysisWeek: int

        // Game context
        TotalGamesY2: float32
        GamesPlayedY2: float32
    }

    /// ML.NET input type for training
    [<CLIMutable>]
    type ModelInput = {
        // Features (all float32 for ML.NET)
        PrevWeekFp: float32
        CurrentWeekFp: float32
        SleeperScore: float32
        FpWeekDelta: float32
        TotalFpY2: float32
        AvgFpY2: float32
        MaxFpY2: float32
        MinFpY2: float32
        FpPerSnapY2: float32
        FpConsistencyY2: float32
        TotalFantasyPointsY1: float32
        PpgY1: float32
        FpPerSnapY1: float32
        W1SnapShare: float32
        Y2SnapShareChange: float32
        SlidingWindowAvgDelta: float32
        MaxSnapPctY2: float32
        MinSnapPctY2: float32
        AvgSnapPctY2: float32
        SnapPctChange: float32
        SnapPctVariance: float32
        SnapConsistencyY2: float32
        TotalOffSnapsY2: float32
        TotalOffSnapsY1: float32
        AvgSnapPctY1: float32
        Crossed10PctSnaps: bool
        Crossed20PctSnaps: bool
        Crossed30PctSnaps: bool
        HasPositiveTrend: bool
        SignificantSnapJump: bool
        Height: float32
        Weight: float32
        Age: float32
        RbSizeScore: float32
        WrHeightScore: float32
        TeSizeScore: float32
        DraftNumber: float32
        YearsExp: float32
        IsUdfa: bool
        IsDay3Pick: bool
        IsEarlyPick: bool
        IsYoungBreakout: bool
        GamesY1: float32
        RookieYearUsage: float32
        OpponentRushDefRank: float32
        OpponentPassDefRank: float32
        Ecr: float32
        PlayerAvailable: float32
        DraftValueScore: float32
        PerformanceScore: float32
        AgeScore: float32
        EcrScore: float32
        MatchupScore: float32
        SnapTrendScore: float32
        TotalGamesY2: float32
        GamesPlayedY2: float32

        // Categorical features
        Position: string
        Team: string
        Opponent: string

        // Label
        Label: bool
    }

    /// ML.NET prediction output
    [<CLIMutable>]
    type ModelOutput = {
        Label: bool
        PredictedLabel: bool
        Score: float32
        Probability: float32
    }

    /// Evaluation metrics for a trained model
    type EvaluationMetrics = {
        AlgorithmName: string
        Auc: float
        Accuracy: float
        Precision: float
        Recall: float
        F1Score: float
        ConfusionMatrix: ConfusionMatrixMetrics
        TrainingTimeSeconds: float
    }

    and ConfusionMatrixMetrics = {
        TruePositives: int
        FalsePositives: int
        TrueNegatives: int
        FalseNegatives: int
    }

    /// Dataset statistics
    type DatasetStats = {
        TotalSamples: int
        TrainingSamples: int
        TestSamples: int
        HitCount: int
        NonHitCount: int
        HitRate: float
        FeatureCount: int
    }

    /// Result of algorithm comparison
    type AlgorithmComparisonResult = {
        DatasetStats: DatasetStats
        AlgorithmResults: EvaluationMetrics list
        BestAlgorithm: EvaluationMetrics
    }
