module DailyFastbreakController

open System
open System.Threading.Tasks
open Giraffe
open Microsoft.AspNetCore.Http
open MongoDB.Driver
open Saturn.Endpoint
open Shared
open Utils.asyncMap
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
      fastbreakCard: EmptyFastbreakCardItem []
      lockedCardForUser: FastbreakSelectionState }
    
let getDailyFastbreakHandler (database: IMongoDatabase) (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastBreakCard>("empty-fastbreak-cards")
        let today = DateTime.Now.ToString("yyyyMMdd")


        let filter = Builders<EmptyFastBreakCard>.Filter.Eq((fun x -> x.date), today)
        
        let! card = collection.Find(filter).FirstOrDefaultAsync()
                       |> Async.AwaitTask
                       |> asyncMap Option.ofObj
        
        let lockedCardForUserAsync =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id ->
                let lockedCards = database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                let f =
                    Builders<FastbreakSelectionState>.Filter.And(
                        Builders<FastbreakSelectionState>.Filter.Eq(_.userId, id),
                        Builders<FastbreakSelectionState>.Filter.Eq(_.date, today)
                    )
            
                lockedCards.Find(f).FirstOrDefaultAsync()
                |> Async.AwaitTask
                |> asyncMap Option.ofObj
            | None -> 
                async { return None } // Return an async with None instead of null
                
        let lockedCardForUser =  lockedCardForUserAsync |> Async.RunSynchronously
                       
        match card with
            | None -> return! json Seq.empty next ctx
            | Some card -> return! json ({fastbreakCard = card.items; leaderboard = [||]; lockedCardForUser = lockedCardForUser.Value}) next ctx
    }

let getScheduleHandler database (next: HttpFunc) (ctx: HttpContext) =
    let schedules = async { return Task.FromResult } |> Async.RunSynchronously
    json schedules next ctx

let dailyFastbreakRouter database =
    router {
        get "/daily" (getDailyFastbreakHandler database)
    }
