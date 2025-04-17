module api.Entities.StatSheet

open System
open MongoDB.Bson.Serialization.Attributes
    
type Streak = { longest: int; current: int }

type FastbreakCard = { date: string; point: int }

type PerfectFastbreakCards = { cards: FastbreakCard[]; highest: FastbreakCard }

type DayInfo = {
    DayOfWeek: string
    DateCode: string
    TotalPoints: int option
}

type CurrentWeek = { days: Map<int, DayInfo>; total: int }

[<BsonIgnoreExtraElements>]
type StatSheetItem =
    { currentWeek: CurrentWeek
      lockedCardStreak: Streak
      highestFastbreakCardEver: FastbreakCard
      perfectFastbreakCards: PerfectFastbreakCards }

[<BsonIgnoreExtraElements>]
type StatSheet = { userId: string; date: string; items: StatSheetItem; createdAt: DateTime; }
