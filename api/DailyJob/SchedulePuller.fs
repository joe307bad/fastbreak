module SchedulePuller

open System
open System.Net.Http
open System.Text.Json
open System.Threading.Tasks
open MongoDB.Driver
open ScheduleEntity


// type Game =
//     { id: string
//       homeTeam: string
//       awayTeam: string
//       location: string
//       homeTeamLogo: string
//       awayTeamLogo: string
//       league: string
//       date: string }


open System
open System.Globalization
open api.Entities.EmptyFastbreakCard

let formatDateParts (isoDateString: string) =
    // Parse the input date
    let dateUtc = DateTimeOffset.Parse(isoDateString)

    // Convert to Eastern Time
    let easternZone =
        TimeZoneInfo.FindSystemTimeZoneById("Eastern Standard Time") // Windows
        |> function
            | tz -> tz

    // Convert the date to Eastern Time
    let date = TimeZoneInfo.ConvertTime(dateUtc, easternZone)

    let dayOfWeek =
        let fullDay = date.DayOfWeek.ToString()

        if fullDay.Length > 6 then
            fullDay.Substring(0, 3) + "."
        else
            fullDay

    // Format month and day (Mar. 5th)
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

    // Format time (@ 1pm)
    let hour = date.Hour % 12
    let hour = if hour = 0 then 12 else hour
    let ampm = if date.Hour >= 12 then "pm" else "am"
    let time = sprintf "@ %d%s" hour ampm

    // Return as tuple
    (dayOfWeek, monthDayWithSuffix, time)

let getTomorrowsSchedulesHandler (schedule: Schedule seq) : Task<EmptyFastbreakCardItem List> =
    task {
        let! schedules =
            async {
                // let collection = database.GetCollection<Schedule>("schedules")
                // let tomorrow = DateTime.Now.ToString("yyyyMMdd")
                // printf $"Tomorrow {tomorrow}"
                // let filter = Builders<Schedule>.Filter.Eq((fun x -> x.date), tomorrow)
                // let! results = collection.Find(filter).ToListAsync() |> Async.AwaitTask

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
                                    |> Option.map (fun obj -> obj.team)
                                    |> Option.defaultValue
                                        { id = null
                                          displayName = null
                                          logo = null }

                                let awayTeam =
                                    competition.competitors
                                    |> Option.bind (Seq.tryFind (fun c -> c.homeAway = "away"))
                                    |> Option.map (fun obj -> obj.team)
                                    |> Option.defaultValue
                                        { id = null
                                          displayName = null
                                          logo = null }

                                let (dayOfWeek, monthDay, time) = formatDateParts event.date

                                { id = event.id
                                  ``type`` = "pick-em"
                                  homeTeam = homeTeam.displayName
                                  homeTeamSubtitle =
                                    competition.venue
                                    |> Option.map (fun obj -> obj.fullName)
                                    |> Option.defaultValue null
                                  awayTeam = awayTeam.displayName
                                  awayTeamSubtitle = null
                                  dateLine1 = dayOfWeek
                                  dateLine2 = monthDay
                                  dateLine3 = time
                                  points = 100
                                  question = null
                                  answer1 = null
                                  answer2 = null
                                  answer3 = null
                                  answer4 = null
                                  correctAnswer = null })))
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
            printfn $"Schedule updated successfully. {league} | {date}\n"
        else
            printfn $"Schedule inserted successfully. {league} | {date}\n"

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

let fetchSchedule (url: string) =
    async {
        use client = new HttpClient()
        let! response = client.GetStringAsync(url) |> Async.AwaitTask
        return response
    }

let parseSchedule (json: string) =
    JsonSerializer.Deserialize<Schedule>(json, JsonSerializerOptions(PropertyNameCaseInsensitive = true))

type RunScheduleUpserts = string -> Task<Schedule> array

let getAllSchedules (runAllAsync: RunScheduleUpserts) (date: string) : Async<seq<Schedule>> =
    async {
        let tasks = runAllAsync date
        let! results = tasks |> Task.WhenAll |> Async.AwaitTask
        return results |> Seq.ofArray
    }


let pullTomorrowsSchedule (database) =
    let dates =
        [ DateTime.UtcNow.AddDays(-1).ToString("yyyyMMdd") // yesterday
          DateTime.UtcNow.ToString("yyyyMMdd") // today
          DateTime.UtcNow.AddDays(1).ToString("yyyyMMdd") ] // tomorrow

    let runAllAsync date =
        urls
        |> List.map (fun (league, espnScheduleEndpoint: Printf.StringFormat<string -> string>) ->
            async {
                let url = sprintf espnScheduleEndpoint date
                let! json = fetchSchedule url
                let schedule = parseSchedule json
                return! insertSchedule (schedule, database, league, date) |> Async.AwaitTask
            }
            |> Async.StartAsTask)
        |> Array.ofList

    let collection: IMongoCollection<EmptyFastBreakCard> =
        database.GetCollection<EmptyFastBreakCard>("empty-fastbreak-cards")




    let insertEmptyFastbreakCard (date: string) =
        async {
            let emptyFastbreakCard =
                getAllSchedules runAllAsync date
                |> Async.RunSynchronously
                |> getTomorrowsSchedulesHandler

            let! cardItems = emptyFastbreakCard |> Async.AwaitTask

            let filter = Builders<EmptyFastBreakCard>.Filter.Eq("date", date)

            let updateOptions = ReplaceOptions(IsUpsert = true)

            let result =
                collection.ReplaceOne(
                    filter,
                    { date = date
                      items = List.toArray cardItems },
                    updateOptions
                )

            printfn $"Empty Fastbreak card inserted for {date}\n"
        }

    dates
    |> List.map insertEmptyFastbreakCard
    |> Async.Parallel
    |> Async.RunSynchronously
// return results
