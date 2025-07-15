module api.Utils.lockedCardsForUser

open MongoDB.Driver
open api.Entities.FastbreakSelections

let getLockedCardForUser (database: IMongoDatabase) (id: string) (date: string) =
    database
        .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
        .Find(
            Builders<FastbreakSelectionState>.Filter
                .And(
                    Builders<FastbreakSelectionState>.Filter.Eq(_.userId, id),
                    Builders<FastbreakSelectionState>.Filter.Eq(_.date, date)
                )
        )
        .FirstOrDefaultAsync()
    |> Async.AwaitTask
    |> Async.RunSynchronously
