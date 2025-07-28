module api.Utils.profile

open System
open MongoDB.Driver
open MongoDB.Bson.Serialization.Attributes

[<BsonIgnoreExtraElements>]
type Profile =
    { userId: string
      googleId: string
      userName: string
      updatedAt: DateTime }

let getUserIdFromProfile (database: IMongoDatabase) (googleId: string) =
    task {
        let collection: IMongoCollection<Profile> = database.GetCollection<Profile>("profiles")
        let filter = Builders<Profile>.Filter.Eq(_.googleId, googleId)
        let! profile = collection.Find(filter).FirstOrDefaultAsync()
        return if not (isNull (box profile)) then Some profile.userId else None
    }

let getUserNameFromUserId (database: IMongoDatabase) (userId: string) =
    task {
        let collection: IMongoCollection<Profile> = database.GetCollection<Profile>("profiles")
        let filter = Builders<Profile>.Filter.Eq(_.userId, userId)
        let! profile = collection.Find(filter).FirstOrDefaultAsync()
        return if not (isNull (box profile)) then profile.userName else "Unknown"
    }