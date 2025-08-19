namespace Fastbreak.Shared.Entities

open MongoDB.Bson.Serialization.Attributes

[<CLIMutable>]
[<BsonIgnoreExtraElements>]
type EmptyFastbreakCardItem =
    { id: string
      ``type``: string
      homeTeam: string
      homeTeamSubtitle: string
      awayTeam: string
      awayTeamSubtitle: string
      dateLine1: string
      dateLine2: string
      dateLine3: string
      points: int
      question: string
      answer1: string
      answer2: string
      answer3: string
      answer4: string
      correctAnswer: string
      date: string }

[<BsonIgnoreExtraElements>]
type EmptyFastbreakCard =
    { date: string
      items: EmptyFastbreakCardItem[] }
