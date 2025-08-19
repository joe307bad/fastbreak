module api.Entities.RefreshToken

open System
open MongoDB.Bson.Serialization.Attributes

[<BsonIgnoreExtraElements>]
type RefreshToken =
    { tokenId: string
      userId: string
      tokenHash: string
      expiresAt: DateTime
      createdAt: DateTime }