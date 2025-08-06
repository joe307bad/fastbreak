module api.Utils.calculateLockedCardStreak

open System
open MongoDB.Driver
open api.Entities.FastbreakSelections
open api.Entities.StatSheet

let calculateLockedCardStreak (database: IMongoDatabase) (statSheet: StatSheet option) userId =
    printf ($"Starting to calculate locked card streak for user {userId}\n")

    let lockedCardsCollection =
        database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

    // Use Eastern Time for consistency with the rest of the app
    let easternZone = TimeZoneInfo.FindSystemTimeZoneById("Eastern Standard Time")
    let easternNow = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, easternZone)
    let today = easternNow.Date
    let yesterday = today.AddDays(-1.0)
    let yesterdayStr = yesterday.ToString("yyyyMMdd")
    
    printf ($"Today: {today:yyyyMMdd}, Yesterday: {yesterdayStr} for user {userId}\n")

    // Check for early return with stat sheet from today only
    // We should only skip calculation if we have today's stat sheet, not yesterday's
    let todayStr = today.ToString("yyyyMMdd")
    match statSheet with
    | Some sheet when sheet.date = todayStr ->
        printf ($"No new locked card streak will be calculated since today's stat sheet was found for user {userId}\n")
        // Today's stat sheet exists, return its values directly
        { longest = sheet.items.lockedCardStreak.longest
          current = sheet.items.lockedCardStreak.current }
    | _ ->
        // Determine query start date based on stat sheet
        let (startDate, existingStreak) =
            match statSheet with
            | None -> 
                printf ($"Calculating new locked card streak using all cards since there is no stat sheet for user {userId}\n")
                (DateTime.MinValue, { longest = 0; current = 0 })
            | Some sheet ->
                let sheetDate = DateTime.ParseExact(sheet.date, "yyyyMMdd", System.Globalization.CultureInfo.InvariantCulture)
                printf ($"Calculating locked card streak starting from {sheet.date} for user {userId}\n")
                (sheetDate, sheet.items.lockedCardStreak)

        // Single optimized query with proper date range  
        let filter =
            Builders<FastbreakSelectionState>.Filter.And(
                Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId),
                Builders<FastbreakSelectionState>.Filter.Gte(_.date, startDate.ToString("yyyyMMdd")),
                Builders<FastbreakSelectionState>.Filter.Lte(_.date, yesterdayStr)
            )

        // Get locked dates efficiently
        let lockedDates =
            lockedCardsCollection
                .Find(filter)
                .ToListAsync()
            |> Async.AwaitTask
            |> Async.RunSynchronously
            |> Seq.map (fun card -> card.date)
            |> Set.ofSeq
            
        let datesString = String.Join(", ", lockedDates |> Set.toArray |> Array.sort)
        printf ($"Found locked dates for user {userId}: {datesString}\n")

        // Efficiently calculate streak using iterative approach
        let calculateStreakFromDate (startDate: DateTime) =
            let mutable currentDate = yesterday
            let mutable streakCount = 0
            let mutable continueStreak = true
            
            printf ($"Starting streak calculation from {yesterdayStr} for user {userId}\n")
            
            // Count backwards from yesterday until we find a gap
            while continueStreak && currentDate >= startDate do
                let dateStr = currentDate.ToString("yyyyMMdd")
                // printf ($"Checking date {dateStr} for user {userId}\n")
                if lockedDates.Contains(dateStr) then
                    streakCount <- streakCount + 1
                    // printf ($"Found locked card on {dateStr}, streak count now {streakCount} for user {userId}\n")
                    currentDate <- currentDate.AddDays(-1.0)
                else
                    printf ($"No locked card found on {dateStr}, ending streak for user {userId}\n")
                    continueStreak <- false
            
            // Check if we can continue the streak from an existing stat sheet
            match statSheet with
            | Some sheet when continueStreak && currentDate >= DateTime.ParseExact(sheet.date, "yyyyMMdd", System.Globalization.CultureInfo.InvariantCulture) ->
                // Streak continues back to the stat sheet date, add existing streak
                let totalCurrent = existingStreak.current + streakCount
                printf $"Continuing streak from stat sheet: {existingStreak.current} + {streakCount} = {totalCurrent} for user {userId}\n"
                { longest = max totalCurrent existingStreak.longest; current = totalCurrent }
            | Some _ when continueStreak ->
                // We've gone past the stat sheet date, but streak is still continuing
                // This means there might be a gap between stat sheet and our current data
                // Add the existing streak from the stat sheet
                let totalCurrent = existingStreak.current + streakCount
                printf $"Continuing streak past stat sheet date: {existingStreak.current} + {streakCount} = {totalCurrent} for user {userId}\n"
                { longest = max totalCurrent existingStreak.longest; current = totalCurrent }
            | _ ->
                // Fresh streak or gap found
                let newCurrent = streakCount
                let newLongest = 
                    match statSheet with
                    | Some _ -> max newCurrent existingStreak.longest
                    | None -> newCurrent
                printf $"Fresh streak calculated: current={newCurrent}, longest={newLongest} for user {userId}\n"
                { longest = newLongest; current = newCurrent }

        calculateStreakFromDate startDate
