namespace Fastbreak.Shared.Utils

module GetStatSheetForUser =

    open MongoDB.Driver
    open Fastbreak.Shared.Entities

    let getStatSheetForUser (database: IMongoDatabase) (id: string) =
        database
            .GetCollection<StatSheet>("user-stat-sheets")
            .Find(Builders<StatSheet>.Filter.Eq(_.userId, id))
            .FirstOrDefaultAsync()
        |> Async.AwaitTask
        |> Async.RunSynchronously
