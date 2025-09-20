module Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict.FantasyBreakoutModels

open Microsoft.ML.Data

// Input data model for training
[<CLIMutable>]
type FantasyBreakoutInput = {
    [<LoadColumn(0)>] PlayerId: string
    [<LoadColumn(1)>] Player: string
    [<LoadColumn(2)>] Position: string
    [<LoadColumn(3)>] Team: string
    [<LoadColumn(4)>] DraftNumber: float32
    [<LoadColumn(5)>] College: string
    [<LoadColumn(6)>] Height: float32
    [<LoadColumn(7)>] Weight: float32
    [<LoadColumn(8)>] Age: float32
    [<LoadColumn(9)>] EntryYear: float32
    [<LoadColumn(10)>] YearsExp: float32
    [<LoadColumn(11)>] GamesPlayedY1: float32
    [<LoadColumn(12)>] TotalFantasyPointsY1: float32
    [<LoadColumn(13)>] PpgY1: float32
    [<LoadColumn(14)>] TotalOffSnapsY1: float32
    [<LoadColumn(15)>] AvgSnapPctY1: float32
    [<LoadColumn(16)>] FpPerSnapY1: float32
    [<LoadColumn(17)>] FpPerGameY1: float32
    [<LoadColumn(18)>] TotalGamesY2: float32
    [<LoadColumn(19)>] AvgSnapPctY2: float32
    [<LoadColumn(20)>] W1SnapShare: float32
    [<LoadColumn(21)>] SnapPctChange: float32
    [<LoadColumn(22)>] Y2SnapShareChange: float32
    [<LoadColumn(23)>] SlidingWindowAvgDelta: float32
    [<LoadColumn(24)>] SnapPctVariance: float32
    [<LoadColumn(25)>] SnapIncreaseMomentum: float32
    [<LoadColumn(26)>] Crossed10pctSnaps: float32
    [<LoadColumn(27)>] Crossed20pctSnaps: float32
    [<LoadColumn(28)>] Crossed30pctSnaps: float32
    [<LoadColumn(29)>] HasPositiveTrend: float32
    [<LoadColumn(30)>] SignificantSnapJump: float32
    [<LoadColumn(31)>] IsUdfa: float32
    [<LoadColumn(32)>] IsDay3Pick: float32
    [<LoadColumn(33)>] IsEarlyPick: float32
    [<LoadColumn(34)>] IsYoungBreakout: float32
    [<LoadColumn(35)>] EliteMatchup: float32
    [<LoadColumn(36)>] GoodMatchup: float32
    [<LoadColumn(37)>] ToughMatchup: float32
    [<LoadColumn(38)>] RbSizeScore: float32
    [<LoadColumn(39)>] WrHeightScore: float32
    [<LoadColumn(40)>] TeSizeScore: float32
    [<LoadColumn(41)>] RookieYearUsage: float32
    [<LoadColumn(42)>] Opponent: string
    [<LoadColumn(43)>] RushDefenseRank: float32
    [<LoadColumn(44)>] PassDefenseRank: float32
    [<LoadColumn(45)>] RelevantDefRank: float32
    [<LoadColumn(46)>] MatchupScore: float32
    [<LoadColumn(47)>] Ecr: float32
    [<LoadColumn(48)>] EcrRangeMin: float32
    [<LoadColumn(49)>] EcrRangeMax: float32
    [<LoadColumn(50)>] DraftValue: float32
    [<LoadColumn(51)>] PerformanceScore: float32
    [<LoadColumn(52)>] AgeScore: float32
    [<LoadColumn(53)>] EcrScore: float32
    [<LoadColumn(54)>] SlidingWindowScore: float32
    [<LoadColumn(55)>] SleeperScore: float32
    [<LoadColumn(56)>] PpgThresholdValue: float32
    [<LoadColumn(57)>] Season: float32
    [<LoadColumn(58)>] PrevWeekFp: float32
    [<LoadColumn(59)>] CurrentWeekFp: float32
    [<LoadColumn(60)>] FpDelta: float32
    // Target variable - will be calculated from FpDelta
    [<ColumnName("Label")>] IsBreakout: bool
}

// Prediction output model
[<CLIMutable>]
type FantasyBreakoutPrediction = {
    [<ColumnName("PredictedLabel")>] IsBreakout: bool
    [<ColumnName("Probability")>] Probability: float32
    [<ColumnName("Score")>] Score: float32
}

// Combined model for evaluation
[<CLIMutable>]
type FantasyBreakoutResult = {
    Player: string
    Position: string
    Team: string
    ActualBreakout: bool
    PredictedBreakout: bool
    Probability: float32
    FpDelta: float32
    SleeperScore: float32
    Week: int
}