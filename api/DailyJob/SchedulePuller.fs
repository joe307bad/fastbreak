module api.DailyJob.SchedulePuller

open System
open System.Net.Http
open System.Text.Json
open System.Threading.Tasks
open api.Entities.EmptyFastbreakCard
open MongoDB.Driver
open api.Entities.ScheduleEntity
open System.Globalization

let ensureIso8601WithSeconds (dateString: string) =
    let parsedDate = DateTimeOffset.Parse(dateString)
    parsedDate.ToString("yyyy-MM-ddTHH:mm:sszzz")

let formatDateParts (isoDateString: string) =
    let dateUtc = DateTimeOffset.Parse(isoDateString)

    let easternZone =
        TimeZoneInfo.FindSystemTimeZoneById("Eastern Standard Time") // Windows
        |> function
            | tz -> tz

    let date = TimeZoneInfo.ConvertTime(dateUtc, easternZone)

    let dayOfWeek =
        let fullDay = date.DayOfWeek.ToString()

        if fullDay.Length > 6 then
            fullDay.Substring(0, 3) + "."
        else
            fullDay

    let monthDay = date.ToString("MMM. d", CultureInfo.InvariantCulture)

    let suffix =
        match date.Day with
        | 1
        | 21
        | 31 -> "st"
        | 2
        | 22 -> "nd"
        | 3
        | 23 -> "rd"
        | _ -> "th"

    let monthDayWithSuffix = monthDay + suffix

    let hour = date.Hour % 12
    let hour = if hour = 0 then 12 else hour
    let ampm = if date.Hour >= 12 then "pm" else "am"
    let time = sprintf "@ %d%s" hour ampm

    (dayOfWeek, monthDayWithSuffix, time)

let getTomorrowsSchedulesHandler (schedule: Schedule seq) : Task<EmptyFastbreakCardItem List> =
    task {
        let! schedules =
            async {
                return
                    schedule
                    |> Seq.collect (fun schedule ->
                        schedule.events
                        |> Seq.collect (fun event ->
                            event.competitions
                            |> Seq.map (fun competition ->
                                let homeTeam =
                                    competition.competitors
                                    |> Option.bind (Seq.tryFind (fun c -> c.homeAway = "home"))
                                    |> Option.map _.team
                                    |> Option.defaultValue
                                        { id = null
                                          displayName = null
                                          logo = null }

                                let awayTeam =
                                    competition.competitors
                                    |> Option.bind (Seq.tryFind (fun c -> c.homeAway = "away"))
                                    |> Option.map _.team
                                    |> Option.defaultValue
                                        { id = null
                                          displayName = null
                                          logo = null }

                                let winner =
                                    match competition.competitors with
                                    | Some competitors ->
                                        competitors
                                        |> Array.tryFind _.winner
                                        |> Option.map _.team.displayName
                                        |> Option.defaultValue null
                                    | None -> null

                                let (dayOfWeek, monthDay, time) = formatDateParts event.date

                                { id = event.id
                                  ``type`` = "PICK-EM"
                                  homeTeam = homeTeam.displayName
                                  homeTeamSubtitle =
                                    competition.venue |> Option.map _.fullName |> Option.defaultValue null
                                  awayTeam = awayTeam.displayName
                                  awayTeamSubtitle = null
                                  date = ensureIso8601WithSeconds event.date
                                  dateLine1 = dayOfWeek
                                  dateLine2 = monthDay
                                  dateLine3 = time
                                  points = 100
                                  question = null
                                  answer1 = null
                                  answer2 = null
                                  answer3 = null
                                  answer4 = null
                                  correctAnswer = winner })))
                    |> Seq.toList
            }
            |> Async.StartAsTask

        return schedules
    }


let insertSchedule (schedule: Schedule, database: IMongoDatabase, league: string, date: string) =
    task {
        let collection = database.GetCollection<Schedule>("schedules")

        let scheduleWithLeague =
            { schedule with
                league = league
                date = date }

        let filter =
            Builders<Schedule>.Filter
                .And(Builders<Schedule>.Filter.Eq("league", league), Builders<Schedule>.Filter.Eq("date", date))

        let updateOptions = ReplaceOptions(IsUpsert = true)

        let result = collection.ReplaceOne(filter, scheduleWithLeague, updateOptions)

        if result.MatchedCount > 0 then
            printf $"Schedule updated successfully. {league} | {date}\n"
        else
            printf $"Schedule inserted successfully. {league} | {date}\n"

        return scheduleWithLeague
    }


let urls =
    [ ("nba",
       Printf.StringFormat<string -> string>
           "http://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=%s")
      ("nhl",
       Printf.StringFormat<string -> string>
           "http://site.api.espn.com/apis/site/v2/sports/hockey/nhl/scoreboard?dates=%s")
      ("nfl",
       Printf.StringFormat<string -> string>
           "http://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=%s")
      ("mlb",
       Printf.StringFormat<string -> string>
           "http://site.api.espn.com/apis/site/v2/sports/baseball/mlb/scoreboard?dates=%s") ]

let parseSchedule (json: string) =
    JsonSerializer.Deserialize<Schedule>(json, JsonSerializerOptions(PropertyNameCaseInsensitive = true))

let fetchSchedules (date: string) =
    use client = new HttpClient()

    let tasks =
        urls
        |> List.map (fun (league, urlFormat) ->
            let url = sprintf urlFormat date

            async {
                try
                    let! response = client.GetStringAsync(url) |> Async.AwaitTask
                    let schedule = parseSchedule response
                    return (schedule, $"Fetched schedule for | date: {date} | league: {league}")
                with ex ->
                    let schedule = parseSchedule null

                    return
                        (schedule,
                         $"Failed to fetch schedule for | date: {date} | league: {league} | error: {ex.Message}")
            })

    let schedules, reports =
        tasks |> Async.Parallel |> Async.RunSynchronously |> Array.unzip

    (schedules |> seq, reports |> List.ofArray)

let fetchSchedule (url: string) =
    async {
        use client = new HttpClient()
        let! response = client.GetStringAsync(url) |> Async.AwaitTask
        return response
    }

type RunScheduleUpserts = string -> Task<Schedule> array

let getAllSchedules (runAllAsync: RunScheduleUpserts) (date: string) : Async<seq<Schedule>> =
    async {
        let tasks = runAllAsync date
        let! results = tasks |> Task.WhenAll |> Async.AwaitTask
        return results |> Seq.ofArray
    }


let pullSchedules
    (
        database: IMongoDatabase,
        twoDaysAgo: String,
        yesterday: String,
        today: String,
        tomorrow: String,
        twoDaysFromNow: String
    ) =
    let dates = [ twoDaysAgo; yesterday; today; tomorrow; twoDaysFromNow ]

    let collection: IMongoCollection<EmptyFastbreakCard> =
        database.GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")

    let insertEmptyFastbreakCard (date) =
        async {
            let (schedules, report) = fetchSchedules date
            let emptyFastbreakCard = schedules |> getTomorrowsSchedulesHandler

            let! cardItems = emptyFastbreakCard |> Async.AwaitTask

            // Insert schedules into schedules collection
            let scheduleInsertTasks =
                schedules
                |> Seq.map (fun schedule ->
                    insertSchedule (schedule, database, schedule.league, date) |> Async.AwaitTask)

            let! _ = scheduleInsertTasks |> Async.Sequential

            let filter = Builders<EmptyFastbreakCard>.Filter.Eq("date", date)

            let updateOptions = ReplaceOptions(IsUpsert = true)

            collection.ReplaceOne(
                filter,
                { date = date
                  items = List.toArray cardItems },
                updateOptions
            )
            |> ignore

            for str in (List.append report [ $"Empty Fastbreak card inserted for {date}\n" ]) do
                printfn "%s" str
        }

    dates
    |> List.map insertEmptyFastbreakCard
    |> Async.Sequential
    |> Async.RunSynchronously
// return results
