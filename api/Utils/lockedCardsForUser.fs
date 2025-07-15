module api.Utils.lockedCardsForUser

open MongoDB.Bson
open MongoDB.Driver
let getLockedCardForUser (database: IMongoDatabase) (id: string) (date: string) =
    database
        .GetCollection("locked-fastbreak-cards")
        .Find(
            Builders<BsonDocument>.Filter
                .And(
                    Builders<BsonDocument>.Filter.Eq("userId", BsonValue.Create(id)),
                    Builders<BsonDocument>.Filter.Eq("date", BsonValue.Create(date))
                )
        )
        .FirstOrDefaultAsync()
    |> Async.AwaitTask
    |> Async.RunSynchronously
