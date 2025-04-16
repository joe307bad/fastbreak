module api.Controllers.DailyFastbreakController

open System
open Giraffe
open Microsoft.AspNetCore.Http
open MongoDB.Driver
open Saturn.Endpoint
open api.Entities.EmptyFastbreakCard
open api.Entities.FastbreakSelections
open api.Utils.asyncMap

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
      fastbreakCard: EmptyFastbreakCardItem[]
      lockedCardForUser: FastbreakSelectionState option }

let getDailyFastbreakHandler (database: IMongoDatabase) (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")
        let today = DateTime.Now.ToString("yyyyMMdd")

        let filter = Builders<EmptyFastbreakCard>.Filter.Eq((fun x -> x.date), today)

        let! card =
            collection.Find(filter).FirstOrDefaultAsync()
            |> Async.AwaitTask
            |> asyncMap Option.ofObj

        let lockedCardForUserAsync =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id ->
                async {
                    let filter =
                        Builders<FastbreakSelectionState>.Filter.Eq(_.userId, id)
                        |> fun f ->
                            Builders<FastbreakSelectionState>.Filter
                                .And(f, Builders<FastbreakSelectionState>.Filter.Eq(_.date, today))

                    let! result =
                        database
                            .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                            .Find(filter)
                            .FirstOrDefaultAsync()
                        |> Async.AwaitTask
                        |> asyncMap Option.ofObj

                    return result
                }
            | None -> async { return None }

        match card with
        | None -> return! json Seq.empty next ctx
        | Some card ->
            return!
                json
                    ({| fastbreakCard = card.items
                        leaderboard = [||]
                        lockedCardForUser =
                         match lockedCardForUserAsync |> Async.RunSynchronously with
                         | Some v -> box v
                         | None -> null |})
                    next
                    ctx
    }
    

let getYesterdaysFastbreakHandler (database: IMongoDatabase) (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")
        let yesterday = DateTime.Now.AddDays(-1).ToString("yyyyMMdd")

        let filter = Builders<EmptyFastbreakCard>.Filter.Eq((fun x -> x.date), yesterday)

        let! card =
            collection.Find(filter).FirstOrDefaultAsync()
            |> Async.AwaitTask
            |> asyncMap Option.ofObj

        let lockedCardForUserAsync =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id ->
                async {
                    let filter =
                        Builders<FastbreakSelectionState>.Filter.Eq(_.userId, id)
                        |> fun f ->
                            Builders<FastbreakSelectionState>.Filter
                                .And(f, Builders<FastbreakSelectionState>.Filter.Eq(_.date, yesterday))

                    let! result =
                        database
                            .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                            .Find(filter)
                            .FirstOrDefaultAsync()
                        |> Async.AwaitTask
                        |> asyncMap Option.ofObj

                    return result
                }
            | None -> async { return None }

        match card with
        | None -> return! json Seq.empty next ctx
        | Some card ->
            return!
                json
                    ({| fastbreakCard = card.items
                        leaderboard = [||]
                        lockedCardForUser =
                         match lockedCardForUserAsync |> Async.RunSynchronously with
                         | Some v -> box v
                         | None -> null |})
                    next
                    ctx
    }

let dailyFastbreakRouter database =
    router {
        get "/daily" (getDailyFastbreakHandler database)
        get "/yesterday" (getYesterdaysFastbreakHandler database)
    }
