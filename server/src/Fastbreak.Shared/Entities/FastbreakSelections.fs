namespace Fastbreak.Shared.Entities

open System
open MongoDB.Bson.Serialization.Attributes


[<CLIMutable>]
[<BsonIgnoreExtraElements>]
type FastbreakSelection =
    { _id: string
      userAnswer: string
      points: int
      description: string
      ``type``: string }

[<CLIMutable>]
[<BsonIgnoreExtraElements>]
type public FastbreakSelectionsResult =
    { totalPoints: int
      totalCorrect: int
      totalIncorrect: int
      correct: string[]
      incorrect: string[]
      date: string }

[<CLIMutable>]
[<BsonIgnoreExtraElements>]
type FastbreakSelectionState =
    { selections: FastbreakSelection[]
      totalPoints: int
      date: string
      cardId: string
      userId: string
      locked: bool
      results: FastbreakSelectionsResult option
      createdAt: DateTime }
