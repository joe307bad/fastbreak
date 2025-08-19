module api.DailyJob.CalculateLeaderboards

open System
open System.Threading.Tasks
open MongoDB.Driver
open Fastbreak.Shared.Entities
open Fastbreak.Shared.Utils.Profile

let calculateLeaderboard
    (database: IMongoDatabase)
    : Task<string * LeaderboardResult> =
    task {
        // Use Eastern Time for consistency
        let easternZone = TimeZoneInfo.FindSystemTimeZoneById("Eastern Standard Time")
        let easternNow = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, easternZone)
        let today = easternNow.Date
        
        // Always generate Sunday-Monday week ranges for current week
        let getCurrentWeekSunday () =
            match today.DayOfWeek with
            | DayOfWeek.Sunday -> today // If it's Sunday, this is the start of the week
            | _ -> 
                // For any other day, find the Sunday that started this week
                let daysSinceSunday = int today.DayOfWeek // Sunday=0, Monday=1, etc.
                today.AddDays(float -daysSinceSunday)

        let currentWeekSunday = getCurrentWeekSunday()
        let currentWeekMondayEnd = currentWeekSunday.AddDays(6.0)
        let sundayId = currentWeekSunday.ToString("yyyyMMdd")
        
        printf $"CalculateLeaderboards: Calculating for week {sundayId} (Sun) to {currentWeekMondayEnd:yyyyMMdd} (Mon)\n"

        // Generate all date codes for the week (Sunday through Monday)
        let allDateCodes = 
            [ 0..6 ]
            |> List.map (fun i -> currentWeekSunday.AddDays(float i).ToString("yyyyMMdd"))

        // Find the most recent stat sheets for this week
        let statSheetsCollection = database.GetCollection<StatSheet>("user-stat-sheets")
        let! statSheets =
            statSheetsCollection
                .Find(Builders<StatSheet>.Filter.Gte(_.createdAt, currentWeekSunday))
                .ToListAsync()

        let statSheetsList = statSheets |> Seq.toList

        // Also get all locked cards for the current week to ensure we capture all points
        let lockedCardsCollection = database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
        let! lockedCards =
            lockedCardsCollection
                .Find(Builders<FastbreakSelectionState>.Filter.In(_.date, allDateCodes))
                .ToListAsync()

        let lockedCardsList = lockedCards |> Seq.toList
        printf $"CalculateLeaderboards: Found {lockedCardsList.Length} locked cards for the week\n"

        let! dailyLeaderboards =
            allDateCodes
            |> List.map (fun dateCode ->
                task {
                    // Get all users who either have stat sheets or locked cards for this date
                    let usersFromStatSheets = 
                        statSheetsList
                        |> List.choose (fun sheet ->
                            let matchingDay = sheet.items.currentWeek.days |> Seq.tryFind (fun kvp -> kvp.DateCode = dateCode)
                            match matchingDay with
                            | Some day when day.TotalPoints.IsSome -> Some (sheet.userId, day.TotalPoints.Value)
                            | _ -> None)
                        |> Map.ofList

                    let usersFromLockedCards = 
                        lockedCardsList
                        |> List.choose (fun card ->
                            if card.date = dateCode then
                                match card.results with
                                | Some results -> Some (card.userId, results.totalPoints)
                                | None -> None
                            else None)
                        |> Map.ofList

                    // Combine all users and their points (prioritizing stat sheets if both exist)
                    let allUsers = 
                        (usersFromStatSheets |> Map.toList) @ (usersFromLockedCards |> Map.toList)
                        |> List.groupBy fst
                        |> List.map (fun (userId, points) ->
                            // If user has both stat sheet and locked card, prefer stat sheet
                            let finalPoints = 
                                match Map.tryFind userId usersFromStatSheets with
                                | Some statSheetPoints -> statSheetPoints
                                | None -> 
                                    match Map.tryFind userId usersFromLockedCards with
                                    | Some lockedCardPoints -> lockedCardPoints
                                    | None -> 0
                            (userId, finalPoints))

                    let! dailyScores =
                        allUsers
                        |> List.map (fun (userId, points) ->
                            task {
                                let! userName = getUserNameFromUserId database userId
                                return { userId = userId; userName = userName; points = points }
                            })
                        |> Task.WhenAll

                    let filteredAndSorted =
                        dailyScores
                        |> Array.filter (fun entry -> entry.points > 0)
                        |> Array.sortByDescending (fun entry -> entry.points)

                    printf $"CalculateLeaderboards: Date {dateCode} has {filteredAndSorted.Length} entries\n"

                    return
                        { dateCode = dateCode
                          entries = filteredAndSorted }
                })
            |> Task.WhenAll

        // Calculate weekly totals by combining stat sheets and locked cards
        let usersWeeklyFromStatSheets = 
            statSheetsList
            |> List.map (fun sheet -> (sheet.userId, sheet.items.currentWeek.total))
            |> Map.ofList

        let usersWeeklyFromLockedCards = 
            lockedCardsList
            |> List.groupBy (fun card -> card.userId)
            |> List.map (fun (userId, cards) ->
                let totalPoints = 
                    cards
                    |> List.sumBy (fun card ->
                        match card.results with
                        | Some results -> results.totalPoints
                        | None -> 0)
                (userId, totalPoints))
            |> Map.ofList

        // Combine all users for weekly totals (prioritizing stat sheets if both exist)
        let allUsersWeekly = 
            let statSheetUsers = usersWeeklyFromStatSheets |> Map.toList
            let lockedCardUsers = usersWeeklyFromLockedCards |> Map.toList
            
            (statSheetUsers @ lockedCardUsers)
            |> List.groupBy fst
            |> List.map (fun (userId, _) ->
                let finalPoints = 
                    match Map.tryFind userId usersWeeklyFromStatSheets with
                    | Some statSheetPoints -> statSheetPoints
                    | None -> 
                        match Map.tryFind userId usersWeeklyFromLockedCards with
                        | Some lockedCardPoints -> lockedCardPoints
                        | None -> 0
                (userId, finalPoints))

        let! weeklyTotals =
            allUsersWeekly
            |> List.map (fun (userId, points) ->
                task {
                    let! userName = getUserNameFromUserId database userId
                    return { userId = userId; userName = userName; points = points }
                })
            |> Task.WhenAll

        let filteredWeeklyTotals =
            weeklyTotals
            |> Array.filter (fun entry -> entry.points > 0)
            |> Array.sortByDescending (fun entry -> entry.points)

        return
            (sundayId, { dailyLeaderboards = dailyLeaderboards
                         weeklyTotals = filteredWeeklyTotals })
    }
