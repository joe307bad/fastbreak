module api.Entities.StatSheet

open System
open MongoDB.Bson.Serialization.Attributes

[<BsonIgnoreExtraElements>]
type DayInfo =
    { DayOfWeek: string
      DateCode: string
      TotalPoints: int option }

[<BsonIgnoreExtraElements>]
type Week = { days: DayInfo array; total: int }

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type Streak = { longest: int; current: int }

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type FastbreakCard = { date: string; points: int }

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type StatSheetItem =
    { currentWeek: Week
      lastWeek: Week
      lockedCardStreak: Streak
      highestFastbreakCardEver: FastbreakCard
      perfectFastbreakCards: FastbreakCard[] }

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type StatSheet =
    { userId: string
      date: string
      items: StatSheetItem
      createdAt: DateTime }
