module api.Utils.createCurrentWeek

open System.Collections.Generic
open MongoDB.Driver
open api.Entities.FastbreakSelections
open api.Entities.StatSheet

let createCurrentWeek (daysOfTheWeek: List<KeyValuePair<string, DayInfo>>) (database: IMongoDatabase) (userId: string) =
    // Convert input days to a list for processing
    let inputDays = daysOfTheWeek |> List.ofSeq
    
    // Process each day and build MongoDB-friendly structure
    let processedDays =
        inputDays
        |> List.map (fun kvp ->
            let dayOfWeek = kvp.Key
            let date = kvp.Value

            let filter =
                Builders<FastbreakSelectionState>.Filter
                    .And(
                        Builders<FastbreakSelectionState>.Filter.Eq(_.date, date.DateCode),
                        Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId)
                    )

            let selectionStateTask =
                database
                    .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                    .Find(filter)
                    .ToListAsync()

            let selectionState = selectionStateTask.Result |> Seq.tryHead

            // Create the MongoDB-friendly day info object
            let updatedDayInfo =
                match selectionState with
                | Some state ->
                    match state.results with
                    | Some results ->
                        { DayOfWeek = date.DayOfWeek
                          DateCode = date.DateCode
                          TotalPoints = Some results.totalPoints }
                    | None ->
                        { DayOfWeek = date.DayOfWeek
                          DateCode = date.DateCode
                          TotalPoints = None }
                | None ->
                    { DayOfWeek = date.DayOfWeek
                      DateCode = date.DateCode
                      TotalPoints = None }
                      
            // Include the original day key in the object itself
            { DayOfWeek = dayOfWeek
              DateCode = date.DateCode
              TotalPoints = updatedDayInfo.TotalPoints }
        )
        |> Array.ofList  // Convert to array for MongoDB compatibility
    
    // Calculate the total points
    let totalPoints =
        processedDays
        |> Array.sumBy (fun day ->
            match day.TotalPoints with
            | Some points -> points
            | None -> 0)
    
    // Return the MongoDB-friendly structure
    { days = processedDays; total = totalPoints }