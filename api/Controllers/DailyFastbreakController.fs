module DailyFastbreakController

open System
open System.Threading.Tasks
open Giraffe
open Microsoft.AspNetCore.Http
open MongoDB.Driver
open Saturn.Endpoint
open ScheduleEntity
open api.Entities.EmptyFastbreakCard

type Game =
    { id: string
      homeTeam: string
      awayTeam: string
      location: string
      homeTeamLogo: string
      awayTeamLogo: string
      league: string
      date: string }

// Define the record to match the JSON object structure
type ScheduleResponse =
    { card: string
      leaderboard: string
      statSheet: string
      week: int
      season: int
      day: int }

[<CLIMutable>]
type LeaderboardItem =
    { id: string
      user: string
      points: int }

[<CLIMutable>]
type DailyFastbreak =
    { leaderboard: LeaderboardItem[]
      fastbreakCard: EmptyFastbreakCardItem [] }

// let getTomorrowsSchedule database =
//     getTomorrowsSchedulesHandler database

let getDailyFastbreakHandler (database: IMongoDatabase) (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastBreakCard>("empty-fastbreak-cards")

        let tomorrow = DateTime.Now.AddDays(1).ToString("yyyyMMdd")

        let filter = Builders<EmptyFastBreakCard>.Filter.Eq((fun x -> x.date), tomorrow)

        // Use FindFirstAsync() to get just the first matching document
        let! card = collection.Find(filter).FirstOrDefaultAsync() |> Async.AwaitTask

        return! json ({fastbreakCard = card.items; leaderboard = [||]}) next ctx
    }

let getScheduleHandler database (next: HttpFunc) (ctx: HttpContext) =
    let schedules = async { return Task.FromResult } |> Async.RunSynchronously
    json schedules next ctx

let dailyFastbreakController database =

    router {
        get "/daily" (getDailyFastbreakHandler database)
        get "/schedule" (getScheduleHandler database)
    }
