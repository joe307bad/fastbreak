module api.Utils.getLockedCardsToAnalyze

open System
open MongoDB.Driver
open api.Entities.StatSheet
open api.Entities.FastbreakSelections

let getLockedCardsToAnalyze (database: IMongoDatabase) (userId: string) (sheet: StatSheet option) : FastbreakSelectionState list =
    // Build the appropriate filter based on whether we have a stat sheet
    let today = DateTime.Now.ToString("yyyyMMdd")
    
    let lockedCardsCollection =
        database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
    let filter =
        match sheet with
        | Some s ->
            Builders<FastbreakSelectionState>.Filter
                .And(
                    Builders<FastbreakSelectionState>.Filter.Eq((fun x -> x.userId), userId),
                    Builders<FastbreakSelectionState>.Filter.Gt((fun x -> x.date), s.date),
                    Builders<FastbreakSelectionState>.Filter.Lt((fun x -> x.date), today)
                )
        | None ->
            Builders<FastbreakSelectionState>.Filter
                .And(
                    Builders<FastbreakSelectionState>.Filter.Eq((fun x -> x.userId), userId),
                    Builders<FastbreakSelectionState>.Filter.Lt((fun x -> x.date), today)
                )
    
    // Execute the query and return as a list
    let mongoList = 
        lockedCardsCollection
            .Find(filter)
            .ToListAsync()
        |> Async.AwaitTask
        |> Async.RunSynchronously
    
    // Convert from MongoDB collection result to F# list
    let resultList = [
        for i in 0 .. mongoList.Count - 1 do
            yield mongoList.[i]
    ]
    
    // Ensure we're returning only locked cards with results
    resultList 
    |> List.filter (fun card -> card.results.IsSome)
    |> List.sortByDescending (fun card -> card.date)