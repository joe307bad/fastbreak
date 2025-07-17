module api.Utils.getWeekDays

open System.Collections.Generic
open api.Entities.StatSheet

let getLastMonday () =
    let today = System.DateTime.Now

    let daysToSubtract =
        match today.DayOfWeek with
        | System.DayOfWeek.Monday -> 0
        | System.DayOfWeek.Sunday -> 6
        | _ -> int today.DayOfWeek - 1

    today.AddDays(float -daysToSubtract)

let getWeekDays () =
    let lastMonday = getLastMonday ()
    let daysList = List<KeyValuePair<string, DayInfo>>()
    
    for i in 0..6 do
        let currentDate = lastMonday.AddDays(float i)
        
        let dayName =
            match i with
            | 0 -> "Monday"
            | 1 -> "Tuesday"
            | 2 -> "Wednesday"
            | 3 -> "Thursday"
            | 4 -> "Friday"
            | 5 -> "Saturday"
            | _ -> "Sunday"
        
        let kvp = KeyValuePair<string, DayInfo>(
            (i + 1).ToString(),
            { DayOfWeek = dayName
              DateCode = currentDate.ToString("yyyyMMdd")
              TotalPoints = None }
        )
        daysList.Add(kvp)
    
    daysList