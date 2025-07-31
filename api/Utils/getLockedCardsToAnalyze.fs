module api.Utils.getLockedCardsToAnalyze

open System
open MongoDB.Driver
open api.Entities.StatSheet
open api.Entities.FastbreakSelections

let getLockedCardsToAnalyze
    (database: IMongoDatabase)
    (userId: string)
    (sheet: StatSheet option)
    : FastbreakSelectionState list =
    let today = DateTime.Now.ToString("yyyyMMdd")

    let collection =
        database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

    // Build filter based on whether sheet is provided
    let dateFilter =
        match sheet with
        | Some s -> Builders<FastbreakSelectionState>.Filter.Gt(_.date, s.date)
        | None -> Builders<FastbreakSelectionState>.Filter.Empty

    let filter =
        Builders<FastbreakSelectionState>.Filter
            .And(
                [ Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId)
                  dateFilter
                  Builders<FastbreakSelectionState>.Filter.Lt(_.date, today) ]
            )

    collection.Find(filter).ToListAsync()
    |> Async.AwaitTask
    |> Async.RunSynchronously
    |> List.ofSeq
    |> List.filter (fun x -> x.results.IsSome)
    |> List.sortByDescending (_.date)
