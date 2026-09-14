namespace Fastbreak.Shared.Utils

module Profile =

    open System
    open MongoDB.Driver
    open MongoDB.Bson.Serialization.Attributes

    [<BsonIgnoreExtraElements>]
    type Profile =
        { userId: string
          userName: string
          updatedAt: DateTime }

    let getUserNameFromUserId (database: IMongoDatabase) (userId: string) =
        task {
            let collection: IMongoCollection<Profile> =
                database.GetCollection<Profile>("profiles")

            let filter = Builders<Profile>.Filter.Eq(_.userId, userId)
            let! profile = collection.Find(filter).FirstOrDefaultAsync()

            return
                if not (isNull (box profile)) then
                    profile.userName
                else
                    "Unknown"
        }
