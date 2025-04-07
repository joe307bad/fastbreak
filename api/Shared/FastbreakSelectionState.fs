module Shared

open MongoDB.Bson.Serialization.Attributes

[<CLIMutable>]
type FastbreakSelection =
    { id: string
      userAnswer: string
      points: int
      description: string
      ``type``: string }
    
[<CLIMutable>]
[<BsonIgnoreExtraElements>]
[<Struct>]
type FastbreakSelectionState =
    { selections: FastbreakSelection[]
      totalPoints: int
      date: string
      cardId: string
      userId: string
      locked: bool }