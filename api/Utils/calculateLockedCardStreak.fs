module api.Utils.calculateLockedCardStreak

open api.Entities.StatSheet

/// Calculate locked card streak information based on CurrentWeek data and existing StatSheet if available
let calculateLockedCardStreak (currentWeek: CurrentWeek) (statSheet: StatSheet option) : Streak =
    // Sort days in currentWeek by their key (day number)
    let sortedDays = 
        currentWeek.days 
        |> Map.toSeq 
        |> Seq.sortBy fst 
        |> Seq.map snd
        |> Seq.toArray
        
    // Helper to check if a day has a locked card
    let hasLockedCard (day: DayInfo) =
        day.TotalPoints.IsSome
    
    // Calculate consecutive days with locked cards from statSheet date or from the beginning of current data
    let calculateConsecutiveDays (startIdx: int) =
        let rec calcDays idx count =
            if idx >= sortedDays.Length then
                count
            elif hasLockedCard sortedDays.[idx] then
                calcDays (idx + 1) (count + 1)
            else
                count
        calcDays startIdx 0
    
    // Find the longest consecutive streak in the current week
    let findLongestStreak () =
        let rec findStreak idx currentStreak maxStreak =
            if idx >= sortedDays.Length then
                max currentStreak maxStreak
            elif hasLockedCard sortedDays.[idx] then
                findStreak (idx + 1) (currentStreak + 1) maxStreak
            else
                findStreak (idx + 1) 0 (max currentStreak maxStreak)
        findStreak 0 0 0
    
    match statSheet with
    | Some sheet ->
        // Get date code from stat sheet (format yyyymmdd)
        let sheetDateCode = sheet.date
        
        // Find the starting day index that matches with the statSheet date
        let startDayIndex = 
            sortedDays 
            |> Array.tryFindIndex (fun day -> day.DateCode = sheetDateCode)
            |> Option.defaultValue 0
        
        // Calculate current streak starting from the stat sheet date
        let currentStreakDays = calculateConsecutiveDays startDayIndex
        
        // Update longest streak if needed
        let longestStreak = max sheet.items.lockedCardStreak.longest (currentStreakDays + sheet.items.lockedCardStreak.current)
        
        { current = sheet.items.lockedCardStreak.current + currentStreakDays; longest = longestStreak }
    
    | None ->
        // No previous stat sheet, calculate streaks based only on current week data
        let currentStreak = 
            // Start from the end of the array and count consecutive days backwards
            let rec countBackwards idx count =
                if idx < 0 then
                    count
                elif hasLockedCard sortedDays.[idx] then
                    countBackwards (idx - 1) (count + 1)
                else
                    count
            countBackwards (sortedDays.Length - 1) 0
        
        let longestStreak = findLongestStreak()
        
        { current = currentStreak; longest = longestStreak }