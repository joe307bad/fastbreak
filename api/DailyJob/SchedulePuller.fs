module SchedulePuller

open System
open System.Net.Http
open System.Text.Json
open MongoDB.Driver
open ScheduleEntity


let insertSchedule (schedule: Schedule, database: IMongoDatabase, league: string, date: string) =
    let collection = database.GetCollection<Schedule>("schedules")

    let scheduleWithLeague =
        { schedule with
            league = league
            date = date }
    
    let filter =
        Builders<Schedule>.Filter.And(
            Builders<Schedule>.Filter.Eq("league", league),
            Builders<Schedule>.Filter.Eq("date", date)
        )
        
    let updateOptions = ReplaceOptions(IsUpsert = true)
    
    let result = collection.ReplaceOne(filter, scheduleWithLeague, updateOptions)

    if result.MatchedCount > 0 then
        printfn $"Schedule updated successfully. {league} | {date}\n"
    else
        printfn $"Schedule inserted successfully. {league} | {date}\n"

let urls =
    [ ("nba", Printf.StringFormat<string -> string> "http://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=%s")
      ("nhl", Printf.StringFormat<string -> string> "http://site.api.espn.com/apis/site/v2/sports/hockey/nhl/scoreboard?dates=%s")
      ("nfl", Printf.StringFormat<string -> string> "http://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=%s")
      ("mlb", Printf.StringFormat<string -> string> "http://site.api.espn.com/apis/site/v2/sports/baseball/mlb/scoreboard?dates=%s") ]

let fetchSchedule (url: string) =
    async {
        use client = new HttpClient()
        let! response = client.GetStringAsync(url) |> Async.AwaitTask
        return response
    }

let parseSchedule (json: string) =
    JsonSerializer.Deserialize<Schedule>(json, JsonSerializerOptions(PropertyNameCaseInsensitive = true))

let pullTomorrowsSchedule (database) =
    async {
        let tomorrow =
            DateTime.UtcNow.AddDays(1).ToString("yyyyMMdd")
        
        let runAllAsync date =
            urls
            |> List.map (fun (league, espnScheduleEndpoint: Printf.StringFormat<string -> string>) -> async {
                let url = sprintf espnScheduleEndpoint date
                let! json = fetchSchedule url
                let schedule = parseSchedule json
                insertSchedule (schedule, database, league, date)
            })
            |> Async.Parallel
            |> Async.RunSynchronously
            
        runAllAsync tomorrow |> ignore
    }
    |> Async.RunSynchronously
