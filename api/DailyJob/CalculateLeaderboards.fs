module api.DailyJob.CalculateLeaderboards

open System
open api.Entities.Leaderboard
open api.Entities.StatSheet

let calculateLeaderboard (statSheets: StatSheet list) (startDateCode: string): LeaderboardResult =
    let generateWeekDateCodes (startDate: string) =
        let startDateTime = DateTime.ParseExact(startDate, "yyyyMMdd", null)
        [0..6]
        |> List.map (fun i -> startDateTime.AddDays(float i).ToString("yyyyMMdd"))
    
    let allDateCodes = generateWeekDateCodes startDateCode
    
    let dailyLeaderboards =
        allDateCodes
        |> List.map (fun dateCode ->
            let dailyScores =
                statSheets
                |> List.map (fun sheet ->
                    let matchingDay = 
                        sheet.items.currentWeek.days
                        |> Seq.tryFind (fun kvp -> kvp.DateCode = dateCode)
                    
                    let points = 
                        match matchingDay with
                        | Some day -> 
                            match day.TotalPoints with
                            | Some pts -> pts
                            | None -> 0
                        | None -> 0
                    
                    { userId = sheet.userId; points = points }
                )
                |> List.filter (fun entry -> entry.points > 0)
                |> List.sortByDescending (fun entry -> entry.points)
                |> Array.ofList
            
            { dateCode = dateCode; entries = dailyScores }
        )
        |> Array.ofList
    
    let weeklyTotals =
        statSheets
        |> List.map (fun sheet -> 
            { userId = sheet.userId; points = sheet.items.currentWeek.total }
        )
        |> List.filter (fun entry -> entry.points > 0)
        |> List.sortByDescending (fun entry -> entry.points)
        |> Array.ofList
    
    { dailyLeaderboards = dailyLeaderboards; weeklyTotals = weeklyTotals }