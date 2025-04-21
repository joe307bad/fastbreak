module api.DailyJob.CalculateStatSheets

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
    (daysOfTheWeek: Map<int, DayInfo>)
    (collection: IMongoCollection<FastbreakSelectionState>)
    (userId: string)
    =
    let days =
        daysOfTheWeek
        |> Map.map (fun dayOfWeek date ->
            let filter =
                Builders<FastbreakSelectionState>.Filter
                    .And(
                        Builders<FastbreakSelectionState>.Filter.Eq(_.date, date.DateCode),
                        Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId)
                    )

            let selectionStateTask = collection.Find(filter).ToListAsync()
            let selectionState = selectionStateTask.Result |> Seq.tryHead

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
                  TotalPoints = None })

    let total =
        days
        |> Map.values
        |> Seq.sumBy (function
            | dayInfo ->
                match dayInfo.TotalPoints with
                | Some points -> points
                | None -> 0)

    { days = days; total = total }

let addLockedCardsToStatSheet sheet lockedCardsSinceStatSheet lockedCardsCollection userId =
    task {
        let daysOfTheWeek = getWeekDays ()
        let currentWeek = createCurrentWeek daysOfTheWeek lockedCardsCollection userId
        let streak = calculateLockedCardStreak lockedCardsSinceStatSheet sheet

        let getHighestFastbreakCards (statSheet: StatSheet option) : FastbreakCard option =
            statSheet |> Option.map (fun state -> state.items.highestFastbreakCardEver)

        let highestFastbreakCardEver =
            findHighestScoringCard lockedCardsSinceStatSheet (getHighestFastbreakCards sheet)

        let getPastPerfectFastbreakCards (statSheet: StatSheet option) : PerfectFastbreakCards option =
            statSheet |> Option.map (fun state -> state.items.perfectFastbreakCards)

        let perfectFastbreakCards =
            getPerfectFastbreakCards lockedCardsSinceStatSheet (getPastPerfectFastbreakCards sheet)

        return
            { currentWeek = currentWeek
              lockedCardStreak = streak
              highestFastbreakCardEver = highestFastbreakCardEver
              perfectFastbreakCards = perfectFastbreakCards }
    }

let getLatestStatSheet (collection: IMongoCollection<StatSheet>) userId =
    task {
        let filter = Builders<StatSheet>.Filter.Eq(_.userId, userId)
        let sort = Builders<StatSheet>.Sort.Descending("createdAt")
        let! result = collection.Find(filter).Sort(sort).Limit(1).ToListAsync()

        return result |> Seq.tryHead
    }

let getUserStatSheets (database: IMongoDatabase) userIds =
    task {
        for userId in userIds do
            let lockedCardsCollection =
                database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

            let statSheetCollection = database.GetCollection<StatSheet>("user-stat-sheets")
            let statSheetTask = getLatestStatSheet statSheetCollection userId

            let latestStatSheet = statSheetTask |> Async.AwaitTask |> Async.RunSynchronously

            match latestStatSheet with
            | Some sheet ->
                let filter =
                    Builders<FastbreakSelectionState>.Filter
                        .And(
                            Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId),
                            Builders<FastbreakSelectionState>.Filter.Gt(_.createdAt, sheet.createdAt)
                        )

                let lockedCardsSinceStatSheet =
                    lockedCardsCollection.Find(filter).ToListAsync()
                    |> Async.AwaitTask
                    |> Async.RunSynchronously

                let newStatSheet =
                    addLockedCardsToStatSheet (Some sheet) lockedCardsSinceStatSheet lockedCardsCollection userId

                printfn $"newStatSheet.Id = {newStatSheet.Id}"
            | None ->
                let filter =
                    Builders<FastbreakSelectionState>.Filter
                        .And(Builders<FastbreakSelectionState>.Filter.Eq((_.userId), userId))

                let lockedCardsForUser =
                    lockedCardsCollection.Find(filter).ToListAsync()
                    |> Async.AwaitTask
                    |> Async.RunSynchronously

                let newStatSheet =
                    addLockedCardsToStatSheet None lockedCardsForUser lockedCardsCollection userId

                printfn $"newStatSheet.Id = {newStatSheet.Id}"

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
                    Builders<StatSheet>.Filter.Eq(_.userId, state.userId),
                    Builders<StatSheet>.Filter.Eq(_.date, state.date)
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

        let yesterdayFilter = Builders<FastbreakSelectionState>.Filter.Eq(_.date, yesterday)

        let userIds =
            database
                .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                .Find(yesterdayFilter)
                .ToEnumerable()
            |> Seq.map _.userId
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
        // let statSheets = [ getStatSheets () ]
        let userStatSheets = getUserStatSheets database userIds

        // 2. (weekly) last weeks total of all fastbreak cards
        // 6. (weekly) number of weekly wins
        // other data:
        // - date the stat sheet was last calculated
        // collection.BulkWrite(toStatSheetWriteModels statSheets) |> ignore
        ""
    }
