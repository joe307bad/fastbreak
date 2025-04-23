module api.Utils.getLockedCardsToAnalyze

open System
open MongoDB.Driver
open api.Entities.StatSheet
open api.Entities.FastbreakSelections

let getLockedCardsToAnalyze (database: IMongoDatabase) (userId: string) (sheet: StatSheet option) : FastbreakSelectionState list =
    let today = DateTime.Now.ToString("yyyyMMdd")
    
    let lockedCardsCollection =
        database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
    let filter =
        match sheet with
        | Some s ->
            Builders<FastbreakSelectionState>.Filter
                .And(
                    Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId),
                    Builders<FastbreakSelectionState>.Filter.Gt(_.date, s.date),
                    Builders<FastbreakSelectionState>.Filter.Lt(_.date, today)
                )
        | None ->
            Builders<FastbreakSelectionState>.Filter
                .And(
                    Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId),
                    Builders<FastbreakSelectionState>.Filter.Lt(_.date, today)
                )
    
    let mongoList = 
        lockedCardsCollection
            .Find(filter)
            .ToListAsync()
        |> Async.AwaitTask
        |> Async.RunSynchronously
    
    let resultList = [
        for i in 0 .. mongoList.Count - 1 do
            yield mongoList.[i]
    ]
    
    resultList 
    |> List.filter _.results.IsSome
    |> List.sortByDescending _.date