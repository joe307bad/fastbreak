module api.Utils.calculateLockedCardStreak

open System
open MongoDB.Driver
open api.Entities.FastbreakSelections
open api.Entities.StatSheet

let calculateLockedCardStreak (database: IMongoDatabase) (statSheet: StatSheet option) userId =
    let lockedCardsCollection =
        database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

    let today = DateTime.Now.ToString("yyyyMMdd")
    let yesterday = DateTime.Now.AddDays(-1).ToString("yyyyMMdd")

    let parseDate (dateStr: string) =
        DateTime.ParseExact(dateStr, "yyyyMMdd", System.Globalization.CultureInfo.InvariantCulture)

    // Check for early return with recent stat sheet
    match statSheet with
    | Some sheet when sheet.date >= yesterday ->
        printf ($"Recent stat sheet found for user {userId}. No new locked card streak will be calculated.\n")
        // Recent stat sheet exists, return its values directly
        { longest = sheet.items.lockedCardStreak.longest
          current = sheet.items.lockedCardStreak.current }
    | None -> 
        printf ($"No stat sheet for user {userId}. Calculating new locked card streak using all cards.\n")
        
        // Get all locked cards for this user
        let lockedCards =
            let filter =
                Builders<FastbreakSelectionState>.Filter
                    .And(Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId))

            lockedCardsCollection.Find(filter).ToListAsync()
            |> Async.AwaitTask
            |> Async.RunSynchronously
            
        // Sort locked cards by date (newest to oldest)
        let sortedCards =
            [ for card in lockedCards -> card.date, card ] |> List.sortByDescending fst

        // Create a set of dates when cards were locked
        let lockedDates = sortedCards |> List.map fst |> Set.ofList

        // Calculate the current streak starting from a given day
        let rec calculateCurrentStreak (currentDate: string) count =
            if not (lockedDates.Contains currentDate) then
                // Found a gap, current streak ends
                count
            else
                // Continue counting
                calculateCurrentStreak ((parseDate currentDate).AddDays(-1.0).ToString("yyyyMMdd")) (count + 1)
                
        // No statSheet provided, calculate streak starting from yesterday
        let current = calculateCurrentStreak yesterday 0
        { longest = current; current = current }
        
    | Some sheet ->
        printf ($"Latest stat sheet for user {userId} is older than yesterday. Calculating the locked card streak starting with {sheet.date}.\n")
        
        // Get locked cards since the date of the stat sheet
        let lockedCards =
            let filter =
                Builders<FastbreakSelectionState>.Filter
                    .And(
                        Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId),
                        Builders<FastbreakSelectionState>.Filter.Gt(_.date, sheet.date),
                        Builders<FastbreakSelectionState>.Filter.Lt(_.date, today)
                    )

            lockedCardsCollection.Find(filter).ToListAsync()
            |> Async.AwaitTask
            |> Async.RunSynchronously
            
        // Sort locked cards by date (newest to oldest)
        let sortedCards =
            [ for card in lockedCards -> card.date, card ] |> List.sortByDescending fst

        // Create a set of dates when cards were locked
        let lockedDates = sortedCards |> List.map fst |> Set.ofList

        // Calculate the current streak starting from a given day
        let rec calculateCurrentStreak (currentDate: string) count =
            if not (lockedDates.Contains currentDate) then
                // Found a gap, current streak ends
                count
            else
                // Continue counting
                calculateCurrentStreak ((parseDate currentDate).AddDays(-1.0).ToString("yyyyMMdd")) (count + 1)
        
        // Check if there's a continuous streak from statSheet date to yesterday
        let rec isStreakFromStatSheetDate (currentDate: string) =
            if currentDate = sheet.date then
                // We've gone past the statSheet date, so streak is continuous
                true
            elif not (lockedDates.Contains currentDate) then
                // Found a gap in the streak
                false
            else
                // Continue checking previous day
                isStreakFromStatSheetDate ((parseDate currentDate).AddDays(-1.0).ToString("yyyyMMdd"))

        if isStreakFromStatSheetDate yesterday then
            // Add statSheet's current streak to number of locked cards since then
            let daysFromStatSheetToYesterday =
                int ((parseDate yesterday) - (parseDate sheet.date)).TotalDays - 1

            let current = sheet.items.lockedCardStreak.current + daysFromStatSheetToYesterday
            let longest = max current sheet.items.lockedCardStreak.longest
            { longest = longest; current = current }
        else
            // Start fresh from yesterday
            let current = calculateCurrentStreak yesterday 0
            { longest = max current sheet.items.lockedCardStreak.longest; current = current }