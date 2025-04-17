module api.Utils.getWeekDays

open api.Entities.StatSheet

let getLastMonday () =
    let today = System.DateTime.Now

    let daysToSubtract =
        match today.DayOfWeek with
        | System.DayOfWeek.Monday -> 7
        | System.DayOfWeek.Sunday -> 6
        | _ -> int today.DayOfWeek - 1

    today.AddDays(float -daysToSubtract)

// Create a sorted map with days of the week starting with Monday
let getWeekDays () =
    let lastMonday = getLastMonday ()

    // Using Map to ensure sorting is guaranteed
    Map.ofList
        [ for i in 0..6 do
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

              // Use integer key for guaranteed sorting (1 for Monday through 7 for Sunday)
              i + 1,
              { DayOfWeek = dayName
                DateCode = currentDate.ToString("yyyyMMdd")
                TotalPoints = None } ]
