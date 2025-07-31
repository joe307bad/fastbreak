module api.Entities.Leaderboard

type LeaderboardEntry =
    { userId: string
      userName: string
      points: int }

type DailyLeaderboard =
    { dateCode: string
      entries: LeaderboardEntry[] }

type LeaderboardResult =
    { dailyLeaderboards: DailyLeaderboard[]
      weeklyTotals: LeaderboardEntry[] }

type Leaderboard =
    { id: string; items: LeaderboardResult }
