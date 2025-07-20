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
        .Sort(Builders<FastbreakSelectionState>.Sort.Descending("createdAt"))
        .FirstOrDefaultAsync()
    |> Async.AwaitTask
    |> Async.RunSynchronously

let getLastLockedCardResultsForUser (database: IMongoDatabase) (id: string) =
    database
        .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
        .Find(
            Builders<FastbreakSelectionState>.Filter
                .And(
                    Builders<FastbreakSelectionState>.Filter.Eq(_.userId, id),
                    Builders<FastbreakSelectionState>.Filter
                        .Not(Builders<FastbreakSelectionState>.Filter.Eq(_.results, None))
                )
        )
        .Sort(Builders<FastbreakSelectionState>.Sort.Descending("createdAt"))
        .FirstOrDefaultAsync()
    |> Async.AwaitTask
    |> Async.RunSynchronously
