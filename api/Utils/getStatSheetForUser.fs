module api.Utils.getStatSheetForUser

open MongoDB.Bson
open MongoDB.Driver

let getStatSheetForUser (database: IMongoDatabase) (id: string) =
    database
        .GetCollection("user-stat-sheets")
        .Find(Builders<BsonDocument>.Filter.Eq("userId", id))
        .FirstOrDefaultAsync()
    |> Async.AwaitTask
    |> Async.RunSynchronously
