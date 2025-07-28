module api.Entities.ScheduleEntity

open MongoDB.Bson.Serialization.Attributes

[<BsonIgnoreExtraElements>]
type Team =
    { id: string
      displayName: string
      logo: string }

[<BsonIgnoreExtraElements>]
type Competitor =
    { id: string
      homeAway: string
      winner: bool
      team: Team }

[<BsonIgnoreExtraElements>]
type Venue = { fullName: string }

[<BsonIgnoreExtraElements>]
type Competition() =
    [<BsonElement("competitors")>]
    member val competitors: Competitor[] option = None with get, set

    [<BsonElement("venue")>]
    member val venue: Venue option = None with get, set

[<BsonIgnoreExtraElements>]
type Event =
    {
      id: string
      date: string
      [<DefaultValue>]
      newProp: string
      competitions: Competition[] }

[<BsonIgnoreExtraElements>]
type Schedule =
    { league: string
      date: string
      events: Event[] }
