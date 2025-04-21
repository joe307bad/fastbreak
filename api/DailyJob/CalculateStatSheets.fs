module api.DailyJob.CalculateStatSheets

open System.Collections.Generic
open api.Entities.FastbreakSelections
open MongoDB.Driver
open api.Entities.StatSheet
open System
open api.Utils.getPerfectFastbreakCards
open api.Utils.findHighestScoringCard
open api.Utils.calculateLockedCardStreak
open api.Utils.getWeekDays

let getWeekDates () =
    let today = DateTime.Now.Date

    let daysToSubtract =
        match int today.DayOfWeek with
        | 0 -> 6
        | n -> n - 1

    let lastMonday = today.AddDays(-daysToSubtract)

    [ for i in 0..6 do
          let date = lastMonday.AddDays(float i)
          let dayName = date.DayOfWeek.ToString()
          let formattedDate = date.ToString("yyyyMMdd")
          yield dayName, formattedDate ]
    |> List.sortBy (fun (dayName, _) ->
        match dayName with
        | "Monday" -> 0
        | "Tuesday" -> 1
        | "Wednesday" -> 2
        | "Thursday" -> 3
        | "Friday" -> 4
        | "Saturday" -> 5
        | "Sunday" -> 6
        | _ -> failwith "Invalid day name")
    |> Map.ofList

let createCurrentWeek
    (daysOfTheWeek: List<KeyValuePair<string, DayInfo>>)
    (database: IMongoDatabase)
    (userId: string)
    =
    let days = List<KeyValuePair<string, DayInfo>>()
    
    for kvp in daysOfTheWeek do
        let dayOfWeek = kvp.Key
        let date = kvp.Value
        
        let filter =
            Builders<FastbreakSelectionState>.Filter
                .And(
                    Builders<FastbreakSelectionState>.Filter.Eq(_.date, date.DateCode),
                    Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId)
                )
        
        let selectionStateTask = database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards").Find(filter).ToListAsync()
        let selectionState = selectionStateTask.Result |> Seq.tryHead
        
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
        
        days.Add(KeyValuePair<string, DayInfo>(dayOfWeek, updatedDayInfo))
    
    let total =
        days
        |> Seq.sumBy (fun kvp ->
            match kvp.Value.TotalPoints with
            | Some points -> points
            | None -> 0)
    
    { days = days; total = total }

let addLockedCardsToStatSheet sheet database userId =
    task {
        printf($"Starting to process stat sheet for user {userId}\n")
        let daysOfTheWeek = getWeekDays ()
        let currentWeek = createCurrentWeek daysOfTheWeek database userId
        let streak = calculateLockedCardStreak database sheet userId

        // let getHighestFastbreakCards (statSheet: StatSheet option) : FastbreakCard option =
        //     statSheet |> Option.map _.items.highestFastbreakCardEver

        // let highestFastbreakCardEver =
        //     findHighestScoringCard database (getHighestFastbreakCards sheet)

        // let getPastPerfectFastbreakCards (statSheet: StatSheet option) : PerfectFastbreakCards option =
        //     statSheet |> Option.map _.items.perfectFastbreakCards

        // let perfectFastbreakCards =
        //     getPerfectFastbreakCards database (getPastPerfectFastbreakCards sheet)

        return
            { currentWeek = currentWeek
              lockedCardStreak = streak
              highestFastbreakCardEver = None
              perfectFastbreakCards = None
              // highestFastbreakCardEver = highestFastbreakCardEver
              // perfectFastbreakCards = perfectFastbreakCards
              }
    }

let getLatestStatSheet (collection: IMongoCollection<StatSheet>) userId =
    task {
        let filter = Builders<StatSheet>.Filter.Eq(_.userId, userId)
        let sort = Builders<StatSheet>.Sort.Descending("date")
        let! result = collection.Find(filter).Sort(sort).Limit(1).ToListAsync()

        return result |> Seq.tryHead
    }

let updateStatSheets (database: IMongoDatabase) userIds =
    task {
        for userId in userIds do

            let statSheetCollection = database.GetCollection<StatSheet>("user-stat-sheets")
            let statSheetTask = getLatestStatSheet statSheetCollection userId

            let latestStatSheet = statSheetTask |> Async.AwaitTask |> Async.RunSynchronously
            let today = DateTime.Now.ToString("yyyyMMdd")
            let yesterday = DateTime.Now.AddDays(-1).ToString("yyyyMMdd")

            let newStatSheet = addLockedCardsToStatSheet latestStatSheet database userId

            // 1. get the users most recent statsheet
            // 2. get all locked cards since that statsheet was created (may need to change the locked cards
            //      and statsheet schema to have something that sortable like number or actual date)
            // 3. calculate the delta in the statsheet based off of the locked cards from the last statsheet
            // 4. create a new stat sheet for the current day

            let filter = Builders<StatSheet>.Filter.Eq("userId", userId)

            let updateOptions = ReplaceOptions(IsUpsert = true)
            let now =  DateTime.Now
            
            try 
                statSheetCollection.ReplaceOne(
                    filter,
                    { date = yesterday
                      items = (newStatSheet |> Async.AwaitTask |> Async.RunSynchronously)
                      createdAt = now
                      userId = userId },
                    updateOptions
                ) |> ignore
                printf($"Stat sheet updated for user {userId} for {yesterday} at {now}\n")
            with ex -> printf($"Stat sheet failed to update for user {userId} for {yesterday} | {ex.Message}\n")
    }

let toStatSheetWriteModels (states: seq<StatSheet>) : seq<WriteModel<StatSheet>> =
    states
    |> Seq.map (fun state ->
        let filter =
            Builders<StatSheet>.Filter
                .And(
                    Builders<StatSheet>.Filter.Eq(_.userId, state.userId),
                    Builders<StatSheet>.Filter.Eq(_.date, state.date)
                )

        let update = ReplaceOneModel(filter, state)
        update.IsUpsert <- true
        upcast update)

let calculateStatSheets (database: IMongoDatabase, twoDaysAgo, yesterday, today, tomorrow) =
    task {
        let yesterdayFilter = Builders<FastbreakSelectionState>.Filter.Eq(_.date, twoDaysAgo)

        let userIds =
            database
                .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                .Find(yesterdayFilter)
                .ToEnumerable()
            |> Seq.map _.userId
            |> Seq.distinct
            |> Seq.toList

        // stat sheet

        // 1. (daily) current week running total of all fastbreak cards
        // 3. (daily) Days in a row locking in a fastbreak card
        // 4. (daily) Highest fastbreak card ever
        // 5. (daily) number of perfect fastbreak card
        // let statSheets = [ getStatSheets () ]
        updateStatSheets database userIds |> ignore

        // 2. (weekly) last weeks total of all fastbreak cards
        // 6. (weekly) number of weekly wins
        // other data:
        // - date the stat sheet was last calculated
        // collection.BulkWrite(toStatSheetWriteModels statSheets) |> ignore
    }
