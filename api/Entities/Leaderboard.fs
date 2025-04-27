module api.Entities.Leaderboard

type LeaderboardEntry = {
    userId: string
    points: int
}

type DailyLeaderboard = {
    dateCode: string
    entries: LeaderboardEntry[]
}

type LeaderboardResult = {
    dailyLeaderboards: DailyLeaderboard[]
    weeklyTotals: LeaderboardEntry[]
}

type LeaderboardHead = { id: string; items: LeaderboardResult; }