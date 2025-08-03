module api.DailyJob.CalculateStatSheets

open api.Entities.FastbreakSelections
open MongoDB.Driver
open api.Entities.StatSheet
open System
open api.Utils.createCurrentWeek
open api.Utils.getLockedCardsToAnalyze
open api.Utils.calculateLockedCardStreak
open api.Utils.getWeekDays
open api.Utils.getHighestAndPerfectCards

let getWeekDates () =
    let today = DateTime.Now.Date

    let daysToSubtract =
        match int today.DayOfWeek with
        | 0 -> 6
        | 1 -> 0
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

let addLockedCardsToStatSheet sheet database userId =
    task {
        printf ($"Starting to process stat sheet for user {userId}\n")
        let daysOfTheWeek = getWeekDays (getLastMonday ())
        let daysOfTheLastWeek = getWeekDays (getOneMondayAgo ())
        let currentWeek = createWeek daysOfTheWeek database userId
        let lastWeek = createWeek daysOfTheLastWeek database userId
        let streak = calculateLockedCardStreak database sheet userId
        let lockedCardsToAnalyze = getLockedCardsToAnalyze database userId sheet

        let (highestFastbreakCardEver, perfectFastbreakCards) =
            getHighestAndPerfectCards lockedCardsToAnalyze sheet

        return
            { currentWeek = currentWeek
              lastWeek = lastWeek
              lockedCardStreak = streak
              highestFastbreakCardEver = highestFastbreakCardEver
              perfectFastbreakCards = perfectFastbreakCards }
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
        for (userId, _) in userIds do
            let statSheetCollection = database.GetCollection<StatSheet>("user-stat-sheets")
            let statSheetTask = getLatestStatSheet statSheetCollection userId

            let latestStatSheet = statSheetTask |> Async.AwaitTask |> Async.RunSynchronously
            let yesterday = DateTime.Now.AddDays(-1).ToString("yyyyMMdd")

            let newStatSheet = addLockedCardsToStatSheet latestStatSheet database userId

            let filter = Builders<StatSheet>.Filter.Eq("userId", userId)

            let updateOptions = ReplaceOptions(IsUpsert = true)
            let now = DateTime.Now

            try
                statSheetCollection.ReplaceOne(
                    filter,
                    { date = yesterday
                      items = (newStatSheet |> Async.AwaitTask |> Async.RunSynchronously)
                      createdAt = now
                      userId = userId },
                    updateOptions
                )
                |> ignore

                printf ($"Stat sheet updated for user {userId} for {yesterday} at {now}\n")
            with ex ->
                printf ($"Stat sheet failed to update for user {userId} for {yesterday} | {ex.Message}\n")
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
        let userIds =
            database
                .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                .Find(
                    Builders<FastbreakSelectionState>.Filter
                        .And(Builders<FastbreakSelectionState>.Filter.Ne(_.results, None))
                )
                .ToEnumerable()
            |> Seq.choose (fun state ->
                match state.results with
                | Some results -> Some(state.userId, results)
                | None -> None)
            |> Seq.distinctBy fst
            |> Seq.toList

        updateStatSheets database userIds |> ignore
    }
