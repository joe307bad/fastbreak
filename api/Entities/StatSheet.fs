module api.Entities.StatSheet

open System
open System.Collections.Generic
open MongoDB.Bson.Serialization.Attributes

type DayInfo = {
    DayOfWeek: string
    DateCode: string
    TotalPoints: int option
}

type CurrentWeek = {
    days: DayInfo array 
    total: int
}

type Streak = { longest: int; current: int }

type FastbreakCard = { date: string; points: int }

type PerfectFastbreakCards = { cards: FastbreakCard[]; highest: FastbreakCard }

[<BsonIgnoreExtraElements>]
type StatSheetItem =
    { currentWeek: CurrentWeek
      lockedCardStreak: Streak
      highestFastbreakCardEver: FastbreakCard
      perfectFastbreakCards: PerfectFastbreakCards }

[<BsonIgnoreExtraElements>]
type StatSheet = { userId: string; date: string; items: StatSheetItem; createdAt: DateTime; }
