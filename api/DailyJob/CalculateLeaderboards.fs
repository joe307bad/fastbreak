module api.DailyJob.CalculateLeaderboards

open System
open System.Threading.Tasks
open MongoDB.Driver
open api.Entities.Leaderboard
open api.Entities.StatSheet
open api.Utils.profile

let calculateLeaderboard
    (database: IMongoDatabase)
    (statSheets: StatSheet list)
    (startDateCode: string)
    : Task<LeaderboardResult> =
    task {
        let generateWeekDateCodes (startDate: string) =
            let startDateTime = DateTime.ParseExact(startDate, "yyyyMMdd", null)

            [ 0..6 ]
            |> List.map (fun i -> startDateTime.AddDays(float i).ToString("yyyyMMdd"))

        let allDateCodes = generateWeekDateCodes startDateCode

        let! dailyLeaderboards =
            allDateCodes
            |> List.map (fun dateCode ->
                task {
                    let! dailyScores =
                        statSheets
                        |> List.map (fun sheet ->
                            task {
                                let matchingDay =
                                    sheet.items.currentWeek.days |> Seq.tryFind (fun kvp -> kvp.DateCode = dateCode)

                                let points =
                                    match matchingDay with
                                    | Some day ->
                                        match day.TotalPoints with
                                        | Some pts -> pts
                                        | None -> 0
                                    | None -> 0

                                let! userName = getUserNameFromUserId database sheet.userId

                                return
                                    { userId = sheet.userId
                                      userName = userName
                                      points = points }
                            })
                        |> Task.WhenAll

                    let filteredAndSorted =
                        dailyScores
                        |> Array.filter (fun entry -> entry.points > 0)
                        |> Array.sortByDescending (fun entry -> entry.points)

                    return
                        { dateCode = dateCode
                          entries = filteredAndSorted }
                })
            |> Task.WhenAll

        let! weeklyTotals =
            statSheets
            |> List.map (fun sheet ->
                task {
                    let! userName = getUserNameFromUserId database sheet.userId

                    return
                        { userId = sheet.userId
                          userName = userName
                          points = sheet.items.currentWeek.total }
                })
            |> Task.WhenAll

        let filteredWeeklyTotals =
            weeklyTotals
            |> Array.filter (fun entry -> entry.points > 0)
            |> Array.sortByDescending (fun entry -> entry.points)

        return
            { dailyLeaderboards = dailyLeaderboards
              weeklyTotals = filteredWeeklyTotals }
    }
