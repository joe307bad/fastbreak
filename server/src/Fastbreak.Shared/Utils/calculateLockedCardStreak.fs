namespace Fastbreak.Shared.Utils

open System
open MongoDB.Driver
open Fastbreak.Shared.Entities

module CalculateLockedCardStreak =

    let calculateLockedCardStreak (database: IMongoDatabase) userId =
        printf ($"Starting to calculate locked card streak for user {userId}\n")

        let lockedCardsCollection =
            database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
        let statSheetCollection = database.GetCollection<StatSheet>("user-stat-sheets")

        // Get the latest stat sheet for this user
        let getLatestStatSheet userId =
            let filter = Builders<StatSheet>.Filter.Eq(_.userId, userId)
            let sort = Builders<StatSheet>.Sort.Descending("date")
            statSheetCollection.Find(filter).Sort(sort).Limit(1).ToListAsync()
            |> Async.AwaitTask
            |> Async.RunSynchronously
            |> Seq.tryHead

        let statSheet = getLatestStatSheet userId

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
        // Determine query approach based on stat sheet
        let (queryFromDate, existingStreak) =
            match statSheet with
            | None -> 
                printf ($"No existing stat sheet found - will calculate streak from all locked cards for user {userId}\n")
                (DateTime.MinValue, { longest = 0; current = 0 })
            | Some sheet ->
                let sheetDate = DateTime.ParseExact(sheet.date, "yyyyMMdd", System.Globalization.CultureInfo.InvariantCulture)
                printf ($"Found stat sheet from {sheet.date} with current streak: {sheet.items.lockedCardStreak.current}, longest: {sheet.items.lockedCardStreak.longest} for user {userId}\n")
                // Query from sheet date forward to check for continuation (include sheet date)
                (sheetDate, sheet.items.lockedCardStreak)

        // Query for locked cards based on our strategy
        let filter =
            Builders<FastbreakSelectionState>.Filter.And(
                Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId),
                Builders<FastbreakSelectionState>.Filter.Gte(_.date, queryFromDate.ToString("yyyyMMdd")),
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

        // Function to calculate streak from all locked cards when no stat sheet exists
        let rec calculateFullStreak () =
            // When no stat sheet exists, get all locked cards and work backwards
            let allCardsFilter = 
                Builders<FastbreakSelectionState>.Filter.And(
                    Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId),
                    Builders<FastbreakSelectionState>.Filter.Lte(_.date, yesterdayStr)
                )
            
            let allLockedDates =
                lockedCardsCollection
                    .Find(allCardsFilter)
                    .ToListAsync()
                |> Async.AwaitTask
                |> Async.RunSynchronously
                |> Seq.map (fun card -> DateTime.ParseExact(card.date, "yyyyMMdd", System.Globalization.CultureInfo.InvariantCulture))
                |> Seq.sort
                |> Seq.rev // Start from most recent
                |> Seq.toList
            
            printf ($"Found {allLockedDates.Length} total locked cards for full calculation for user {userId}\n")
            
            let mutable currentStreak = 0
            let mutable longestStreak = 0
            let mutable expectedDate = yesterday
            
            for lockedDate in allLockedDates do
                if lockedDate = expectedDate then
                    currentStreak <- currentStreak + 1
                    longestStreak <- max longestStreak currentStreak
                    expectedDate <- expectedDate.AddDays(-1.0)
                elif lockedDate < expectedDate then
                    // Gap found - reset current streak and continue looking for new streaks
                    longestStreak <- max longestStreak currentStreak
                    currentStreak <- 1 // This card starts a new streak
                    expectedDate <- lockedDate.AddDays(-1.0)
                // else: future date, skip
            
            printf ($"Full calculation complete - current: {currentStreak}, longest: {longestStreak} for user {userId}\n")
            { longest = longestStreak; current = currentStreak }

        // Calculate current streak by working backwards from yesterday
        let calculateCurrentStreak () =
            let mutable currentDate = yesterday
            let mutable newStreakCount = 0
            let mutable continueStreak = true
            let mutable connectedToExistingStreak = false
            
            printf ($"Calculating current streak backwards from {yesterdayStr} for user {userId}\n")
            
            // Count backwards from yesterday until we find a gap or hit our query boundary
            while continueStreak do
                let dateStr = currentDate.ToString("yyyyMMdd")
                if lockedDates.Contains(dateStr) then
                    newStreakCount <- newStreakCount + 1
                    printf ($"Found locked card on {dateStr}, new streak count now {newStreakCount} for user {userId}\n")
                    currentDate <- currentDate.AddDays(-1.0)
                    
                    // If we have a stat sheet and we've reached its date, check if we can continue the existing streak
                    match statSheet with
                    | Some sheet when currentDate.ToString("yyyyMMdd") = sheet.date ->
                        // We've connected to the existing streak
                        printf ($"Reached existing stat sheet date {sheet.date} for user {userId}\n")
                        connectedToExistingStreak <- true
                        continueStreak <- false
                    | _ -> ()
                else
                    printf ($"No locked card found on {dateStr}, streak ends for user {userId}\n")
                    continueStreak <- false
            
            // Determine the result based on what we found
            if connectedToExistingStreak then
                // We've connected to the existing streak - add them together
                let totalCurrent = existingStreak.current + newStreakCount
                printf ($"Connected to existing streak: {existingStreak.current} + {newStreakCount} = {totalCurrent} for user {userId}\n")
                let newLongest = max totalCurrent existingStreak.longest
                { longest = newLongest; current = totalCurrent }
            else
                // Either we found a gap or we have no stat sheet
                match statSheet with
                | Some _ -> 
                    // We have a stat sheet but found a gap - current streak is just the new count
                    let newLongest = max newStreakCount existingStreak.longest
                    printf ($"Gap found - current streak: {newStreakCount}, longest: {newLongest} for user {userId}\n")
                    { longest = newLongest; current = newStreakCount }
                | None ->
                    // No stat sheet - need to calculate from all cards
                    printf ($"No stat sheet - calculating full streak for user {userId}\n")
                    calculateFullStreak ()
            
        calculateCurrentStreak ()
