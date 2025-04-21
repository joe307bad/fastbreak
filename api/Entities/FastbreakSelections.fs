module api.Entities.FastbreakSelections

open System
open MongoDB.Bson.Serialization.Attributes


[<CLIMutable>]
type FastbreakSelection =
    { id: string
      userAnswer: string
      points: int
      description: string
      ``type``: string }
    
[<CLIMutable>]
type public FastbreakSelectionsResult =
    { totalPoints: int
      totalCorrect: int
      totalIncorrect: int
      correct: string[]
      incorrect: string[] }

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
