module api.Entities.StatSheet

open System
open System.Collections.Generic
open MongoDB.Bson.Serialization.Attributes

type FastbreakCard = { date: string; points: int }

type PerfectFastbreakCards = { cards: FastbreakCard[]; highest: FastbreakCard }

type DayInfo = {
    DayOfWeek: string
    DateCode: string
    TotalPoints: int option
}

type CurrentWeek = {
    days: List<KeyValuePair<string, DayInfo>> 
    total: int
}

type Streak = { longest: int; current: int }

[<BsonIgnoreExtraElements>]
type StatSheetItem =
    { currentWeek: CurrentWeek
      lockedCardStreak: Streak
      highestFastbreakCardEver: FastbreakCard option
      perfectFastbreakCards: PerfectFastbreakCards option }

[<BsonIgnoreExtraElements>]
type StatSheet = { userId: string; date: string; items: StatSheetItem; createdAt: DateTime; }
