module api.DailyJob.CalculateStatSheets

open api.Entities.FastbreakSelections
open MongoDB.Driver
open api.Entities.StatSheet
open System

let getStatSheets () =
    // Helper to generate a random date within the last 30 days
    let randomRecentDate () =
        let now = DateTime.Now
        let daysAgo = Random().Next(0, 30)
        now.AddDays(-daysAgo).ToString("yyyy-MM-dd")

    // Helper to generate random points (1-100)
    let randomPoints () = Random().Next(1, 101)

    // Create fake FastbreakCard
    let createFakeCard () =
        { date = randomRecentDate ()
          point = randomPoints () }

    // Create a fake list of daily selections for the current week
    let fakeDailySelections =
        [| "Monday"
           "Tuesday"
           "Wednesday"
           "Thursday"
           "Friday"
           "Saturday"
           "Sunday" |]
        |> Array.fold
            (fun map day ->
                // Randomly choose between Result and NoLockedCard
                let selection =
                    if Random().Next(0, 5) > 0 then // 80% chance of having a Result
                        Result(randomPoints ())
                    else
                        NoLockedCard

                Map.add day selection map)
            Map.empty

    // Calculate total points from selections
    let totalPoints =
        fakeDailySelections
        |> Map.toSeq
        |> Seq.sumBy (fun (_, selection) ->
            match selection with
            | Result points -> points
            | NoLockedCard -> 0)

    // Create 3-5 random cards for perfectFastbreakCards
    let numberOfCards = Random().Next(3, 6)
    let fastbreakCards = Array.init numberOfCards (fun _ -> createFakeCard ())

    // Find the highest card in the collection
    let highestCard = fastbreakCards |> Array.maxBy (fun card -> card.point)

    // Create a high-value card for highestFastbreakCardEver
    let highestEverCard =
        let record = createFakeCard ()

        { record with
            point = max record.point (highestCard.point + Random().Next(5, 20)) }

    // Create a StatSheet
    let statSheet =
        { userId = Guid.NewGuid().ToString()
          date = DateTime.Now.ToString("yyyy-MM-dd")
          createdAt = DateTime.Now
          items =
            { currentWeek =
                { days = fakeDailySelections
                  total = totalPoints }
              lockedCardStreak =
                { longest = Random().Next(5, 30)
                  current = Random().Next(0, 10) }
              highestFastbreakCardEver = highestEverCard
              perfectFastbreakCards =
                { cards = fastbreakCards
                  highest = highestCard } } }

    statSheet

open System

let getWeekDates () =
    let today = DateTime.Now.Date
    
    let daysToSubtract = 
        match int today.DayOfWeek with
        | 0 -> 6
        | n -> n - 1
    
    let lastMonday = today.AddDays(-daysToSubtract)
    
    [
        for i in 0..6 do
            let date = lastMonday.AddDays(float i)
            let dayName = date.DayOfWeek.ToString()
            let formattedDate = date.ToString("yyyyMMdd")
            yield dayName, formattedDate
    ] |> Map.ofList


let createCurrentWeek (daysOfTheWeek: Map<string, string>) (collection: IMongoCollection<FastbreakSelectionState>) (userId: string) =
    let days =
        daysOfTheWeek
        |> Map.map (fun dayOfWeek date ->
            let filter = 
                Builders<FastbreakSelectionState>.Filter.And(
                    Builders<FastbreakSelectionState>.Filter.Eq((fun x -> x.date), date),
                    Builders<FastbreakSelectionState>.Filter.Eq((fun x -> x.userId), userId)
                )
            
            let selectionStateTask = collection.Find(filter).ToListAsync()
            let selectionState = selectionStateTask.Result |> Seq.tryHead
            
            match selectionState with
            | Some state ->
                match state.results with
                | Some results -> Result results.totalPoints
                | None -> NoLockedCard
            | None -> NoLockedCard
        )
    
    let total =
        days
        |> Map.values
        |> Seq.sumBy (function
            | Result points -> points
            | NoLockedCard -> 0
        )
    
    { days = days; total = total }

let addLockedCardsToStatSheet sheet lockedCardsSinceStatSheet lockedCardsCollection userId =
    task {
        let daysOfTheWeek = getWeekDates()
        let currentWeek = createCurrentWeek daysOfTheWeek lockedCardsCollection userId
        ""
    }

let getLatestStatSheet (collection: IMongoCollection<StatSheet>) userId =
    task {
        let filter = Builders<StatSheet>.Filter.Eq((fun x -> x.userId), userId)
        let sort = Builders<StatSheet>.Sort.Descending("createdAt")
        let! result =
            collection
                .Find(filter)
                .Sort(sort)
                .Limit(1)
                .ToListAsync()
        
        return result |> Seq.tryHead
    }

let getUserStatSheets (database: IMongoDatabase) userIds =
    task {
        for userId in userIds do
            let lockedCardsCollection = database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards");
            let statSheetCollection = database.GetCollection<StatSheet>("user-stat-sheets");
            let statSheetTask = getLatestStatSheet statSheetCollection userId

            let latestStatSheet = 
                statSheetTask
                |> Async.AwaitTask
                |> Async.RunSynchronously
                
            match latestStatSheet with
                | Some sheet ->
                    let filter =
                        Builders<FastbreakSelectionState>.Filter.And(
                            Builders<FastbreakSelectionState>.Filter.Eq((fun s -> s.userId), userId),
                            Builders<FastbreakSelectionState>.Filter.Gt((fun s -> s.createdAt), sheet.createdAt)
                        )
                    let lockedCardsSinceStatSheet =
                        lockedCardsCollection.Find(filter).ToListAsync()
                        |> Async.AwaitTask
                        |> Async.RunSynchronously
                    let newStatSheet = addLockedCardsToStatSheet sheet lockedCardsSinceStatSheet lockedCardsCollection userId
                    printfn "Found latest stat sheet created at %A" sheet.createdAt
                | None ->
                    let filter =
                        Builders<FastbreakSelectionState>.Filter.And(
                            Builders<FastbreakSelectionState>.Filter.Eq((fun s -> s.userId), userId)
                        )
                    let lockedCardsForUser =
                        lockedCardsCollection.Find(filter).ToListAsync()
                        |> Async.AwaitTask
                        |> Async.RunSynchronously
                    let newStatSheet = addLockedCardsToStatSheet None lockedCardsForUser lockedCardsCollection userId
                    printfn "No stat sheet found for user %s" userId
            ignore
            // 1. get the users most recent statsheet
            // 2. get all locked cards since that statsheet was created (may need to change the locked cards
            //      and statsheet schema to have something that sortable like number or actual date)
            // 3. calculate the delta in the statsheet based off of the locked cards from the last statsheet
            // 4. create a new stat sheet for the current day
            
            // let filter =
            //     Builders<StatSheet>.Filter
            //         .And(
            //             Builders<StatSheet>.Filter.Eq(_.userId, userId),
            //             Builders<StatSheet>.Filter.Gt(_.date, yesterday)
            //         )
            // let lockedCards = database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards").Find(yesterdayFilter)
        ""
    }

let toStatSheetWriteModels (states: seq<StatSheet>) : seq<WriteModel<StatSheet>> =
    states
    |> Seq.map (fun state ->
        let filter =
            Builders<StatSheet>.Filter
                .And(
                    Builders<StatSheet>.Filter.Eq((fun s -> s.userId), state.userId),
                    Builders<StatSheet>.Filter.Eq((fun s -> s.date), state.date)
                )

        let update = ReplaceOneModel(filter, state)
        update.IsUpsert <- true
        upcast update)

let calculateStatSheets (database: IMongoDatabase, yesterday, today, tomorrow) =
    task {
        let collection: IMongoCollection<StatSheet> =
            database.GetCollection<StatSheet>("user-statsheets")

        let filter =
            Builders<StatSheet>.Filter
                .And(
                    Builders<StatSheet>.Filter.Eq(_.userId, "107604865191991316946"),
                    Builders<StatSheet>.Filter.Eq(_.date, yesterday)
                )

        let yesterdayFilter =
            Builders<FastbreakSelectionState>.Filter.Eq((fun x -> x.date), yesterday)

        let userIds =
            database
                .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                .Find(yesterdayFilter)
                .ToEnumerable()
            |> Seq.map (fun card -> card.userId)
            |> Seq.distinct
            |> Seq.toList

        let filter =
            Builders<StatSheet>.Filter
                .And(
                    Builders<StatSheet>.Filter.Eq(_.userId, "107604865191991316946"),
                    Builders<StatSheet>.Filter.Eq(_.date, yesterday)
                )

        // stat sheet

        // 1. (daily) current week running total of all fastbreak cards
        // 3. (daily) Days in a row locking in a fastbreak card
        // 4. (daily) Highest fastbreak card ever
        // 5. (daily) number of perfect fastbreak card
        let statSheets = [ getStatSheets () ]
        let userStatSheets = getUserStatSheets database userIds

        // 2. (weekly) last weeks total of all fastbreak cards
        // 6. (weekly) number of weekly wins
        // other data:
        // - date the stat sheet was last calculated
        // collection.BulkWrite(toStatSheetWriteModels statSheets) |> ignore
        ""
    }
