module api.Utils.getStatSheetForUser

open MongoDB.Driver
open api.Entities.StatSheet

let getStatSheetForUser (database: IMongoDatabase) (id: string) =
    database
        .GetCollection<StatSheet>("user-stat-sheets")
        .Find(Builders<StatSheet>.Filter.Eq(_.userId, id))
        .FirstOrDefaultAsync()
    |> Async.AwaitTask
    |> Async.RunSynchronously
