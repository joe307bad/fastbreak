module api.DailyJob.DailyJob

open System
open MongoDB.Driver
open api.DailyJob.CalculateFastbreakCardResults
open api.DailyJob.CalculateLeaderboards
open api.DailyJob.CalculateStatSheets
open api.DailyJob.SchedulePuller
open api.Entities.Leaderboard
open api.Entities.StatSheet
open api.Utils.getWeekDays

let getEasternTime (addDays) =
    let utcNow = DateTime.UtcNow.AddDays(addDays)
    let easternZone = TimeZoneInfo.FindSystemTimeZoneById("Eastern Standard Time")
    let easternTime = TimeZoneInfo.ConvertTimeFromUtc(utcNow, easternZone)

    let formatted = easternTime.ToString("yyyyMMdd")
    let dateTime = easternTime

    (formatted, dateTime)

let dailyJob enableSchedulePuller database =
    let (twoDaysAgo, _) = getEasternTime (-2)
    let (yesterday, _) = getEasternTime (-1)
    let (today, now) = getEasternTime (0)
    let (tomorrow, _) = getEasternTime (1)
    let (twoDaysFromNow, _) = getEasternTime (2)
    printf $"Daily job started at %A{now}\n"

    // run at 4 am ET every day - this will hopefully get results for any games
    // that started during primetime on the West coast.
    if enableSchedulePuller then
        try
            pullSchedules (database, twoDaysAgo, yesterday, today, tomorrow, twoDaysFromNow) |> ignore
            let (_, now) = getEasternTime (0)
            printf $"Schedule puller completed at %A{now}\n"
        with ex ->
            let (_, now) = getEasternTime (0)
            printf $"Schedule puller failed at %A{now} with error {now}\n"
    else
        let (_, now) = getEasternTime (0)
        printf $"Schedule puller did not run at %A{now} because its disabled\n"

    // Calculation functions have been moved to separate Hangfire jobs that run at 4am ET

    let (_, now) = getEasternTime (0)
    printf $"Daily job completed at %A{now}\n"
