module api.Entities.StatSheet

open System
open MongoDB.Bson.Serialization.Attributes
open api.Entities.FastbreakSelections

[<BsonIgnoreExtraElements>]
type DayInfo = {
    DayOfWeek: string
    DateCode: string
    TotalPoints: int option
}

[<BsonIgnoreExtraElements>]
type CurrentWeek = {
    days: DayInfo array 
    total: int
}

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type Streak = { longest: int; current: int }

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type FastbreakCard = { date: string; points: int }

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type PerfectFastbreakCards = { cards: FastbreakCard[]; highest: FastbreakCard }

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type StatSheetItem =
    { currentWeek: CurrentWeek
      lockedCardStreak: Streak
      highestFastbreakCardEver: FastbreakCard
      perfectFastbreakCards: PerfectFastbreakCards
      cardResults: FastbreakSelectionsResult }

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type StatSheet = { userId: string; date: string; items: StatSheetItem; createdAt: DateTime; }
