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

    try
        let (_, now) = getEasternTime (0)
        calculateFastbreakCardResults database |> ignore
        printf $"Fastbreak card results completed at %A{now}\n"
    with ex ->
        let (_, now) = getEasternTime (0)
        printf $"Fastbreak card results failed at %A{now} with error {ex.Message}\n"

    try
        let (_, now) = getEasternTime (0)
        calculateStatSheets (database, twoDaysAgo, yesterday, today, tomorrow) |> ignore
        printf $"Fastbreak stat sheets completed at %A{now}\n"
    with ex ->
        let (_, now) = getEasternTime (0)
        printf $"Stat sheet failed at %A{now} with error {ex.Message}\n"

    try
        let (_, now) = getEasternTime (0)

        let monday = getLastMonday ()
        let mondayId = monday.ToString("yyyyMMdd")

        let statSheets =
            database
                .GetCollection<StatSheet>("user-stat-sheets")
                .Find(Builders<StatSheet>.Filter.Gte(_.createdAt, monday))
                .ToList()
            |> Seq.toList

        let leaderboards = calculateLeaderboard statSheets mondayId

        database
            .GetCollection<Leaderboard>("leaderboards")
            .ReplaceOne(
                Builders<Leaderboard>.Filter.Eq(_.id, mondayId),
                { id = mondayId; items = leaderboards },
                ReplaceOptions(IsUpsert = true)
            ) |> ignore

        printf $"Fastbreak leaderboard completed at %A{now} for week {mondayId}\n"
    with ex ->
        let (_, now) = getEasternTime (0)
        printf $"Leaderboards failed at %A{now} with error {ex.Message}\n"

    let (_, now) = getEasternTime (0)
    printf $"Daily job completed at %A{now}\n"
