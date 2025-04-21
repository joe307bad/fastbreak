module api.Utils.calculateLockedCardStreak

open System
open api.Entities.FastbreakSelections
open api.Entities.StatSheet

open System.Collections.Generic

let calculateLockedCardStreak (lockedCards: List<FastbreakSelectionState>) (statSheet: StatSheet option) =
    // Helper to convert date string (yyyymmdd) to DateTime
    let parseDate (dateStr: string) = 
        DateTime.ParseExact(dateStr, "yyyyMMdd", System.Globalization.CultureInfo.InvariantCulture)
    
    // Get yesterday's date
    let yesterday = DateTime.Now.AddDays(-1.0).Date
    
    // Sort locked cards by date (newest to oldest)
    let sortedCards = 
        [for card in lockedCards -> parseDate card.date, card]
        |> List.sortByDescending fst
    
    // Create a set of dates when cards were locked
    let lockedDates = 
        sortedCards
        |> List.map fst
        |> Set.ofList
    
    // Calculate the current streak starting from yesterday
    let rec calculateCurrentStreak (currentDate: DateTime) count =
        if not (lockedDates.Contains currentDate) then
            // Found a gap, current streak ends
            count
        else
            // Continue counting
            calculateCurrentStreak (currentDate.AddDays(-1.0)) (count + 1)
    
    match statSheet with
    | None ->
        // No statSheet provided, calculate streak starting from yesterday
        let current = calculateCurrentStreak yesterday 0
        { longest = current; current = current }
        
    | Some sheet ->
        // Check if continuous streak from statSheet date exists
        let statSheetDate = parseDate sheet.date
        
        // Check if there's a continuous streak from statSheet date to yesterday
        let rec isStreakFromStatSheetDate (currentDate: DateTime) =
            if currentDate < statSheetDate then
                // We've gone past the statSheet date, so streak is continuous
                true
            elif not (lockedDates.Contains currentDate) then
                // Found a gap in the streak
                false
            else
                // Continue checking previous day
                isStreakFromStatSheetDate (currentDate.AddDays(-1.0))
        
        if isStreakFromStatSheetDate yesterday then
            // Add statSheet's current streak to number of locked cards since then
            let daysFromStatSheetToYesterday = 
                int (yesterday - statSheetDate).TotalDays + 1
            let current = sheet.items.lockedCardStreak.current + daysFromStatSheetToYesterday
            let longest = max current sheet.items.lockedCardStreak.longest
            { longest = longest; current = current }
        else
            // Start fresh from yesterday
            let current = calculateCurrentStreak yesterday 0
            { longest = current; current = current }