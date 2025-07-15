module api.Controllers.DailyFastbreakController

open System
open Giraffe
open Microsoft.AspNetCore.Http
open MongoDB.Driver
open Saturn.Endpoint
open api.Entities.StatSheet
open api.Entities.EmptyFastbreakCard
open api.Entities.FastbreakSelections
open api.Entities.Leaderboard
open api.Utils.getStatSheetForUser
open api.Utils.asyncMap
open api.Utils.lockedCardsForUser

type Game =
    { id: string
      homeTeam: string
      awayTeam: string
      location: string
      homeTeamLogo: string
      awayTeamLogo: string
      league: string
      date: string }

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
      lockedCardForUser: FastbreakSelectionState option
      statSheetForUser: StatSheet option }

let getDailyFastbreakHandler (database: IMongoDatabase) (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")

        let leaderboard =
            database
                .GetCollection<LeaderboardHead>("leaderboards")
                .Find(fun _ -> true)
                .FirstOrDefault()

        let today = DateTime.Now.ToString("yyyyMMdd")

        let filter = Builders<EmptyFastbreakCard>.Filter.Eq(_.date, today)

        let! card =
            collection.Find(filter).FirstOrDefaultAsync()
            |> Async.AwaitTask
            |> asyncMap Option.ofObj

        let statSheetForUser =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id -> 
                getStatSheetForUser database id |> Option.ofObj
            | None -> None

        let lockedCard =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id -> 
                getLockedCardForUser database id today |> Option.ofObj
            | None -> None

        match card with
        | None -> return! json Seq.empty next ctx
        | Some card ->
            let safeFastbreakCard = if isNull card.items then [||] else card.items
            return!
                json
                    ({| statSheetForUser = statSheetForUser
                        fastbreakCard = card.items
                        leaderboard = leaderboard.items
                        lockedCardForUser = lockedCard |})
                    next
                    ctx
    }


let getYesterdaysFastbreakHandler (database: IMongoDatabase) (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")
        let yesterday = DateTime.Now.AddDays(-1).ToString("yyyyMMdd")

        let leaderboard =
            database
                .GetCollection<LeaderboardHead>("leaderboards")
                .Find(fun _ -> true)
                .FirstOrDefault()

        let filter = Builders<EmptyFastbreakCard>.Filter.Eq(_.date, yesterday)

        let! card =
            collection.Find(filter).FirstOrDefaultAsync()
            |> Async.AwaitTask
            |> asyncMap Option.ofObj

        let lockedCard =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id -> 
                getLockedCardForUser database id yesterday |> Option.ofObj
            | None -> None

        let statSheetForUser =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id -> 
                getStatSheetForUser database id |> Option.ofObj
            | None -> None
            
        match card with
        | None -> return! json Seq.empty next ctx
        | Some card ->
            let safeFastbreakCard = if isNull card.items then [||] else card.items
            return!
                json
                    ({| statSheetForUser = statSheetForUser
                        fastbreakCard = card.items
                        leaderboard = leaderboard.items
                        lockedCardForUser = lockedCard |})
                    next
                    ctx
    }

let getFastbreakHandler (database: IMongoDatabase) day (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")

        let filter = Builders<EmptyFastbreakCard>.Filter.Eq(_.date, day)

        let! card =
            collection.Find(filter).FirstOrDefaultAsync()
            |> Async.AwaitTask
            |> asyncMap Option.ofObj

        let lockedCard =
            async {
                match ctx.TryGetQueryStringValue "userId" with
                | Some id -> return box (getLockedCardForUser database id day)
                | None -> return null
            }
            |> Async.RunSynchronously

        match card with
        | None -> return! json Seq.empty next ctx
        | Some card ->
            return!
                json
                    ({| lockedCardForUser = lockedCard
                        fastbreakCard = [||]
                        leaderboard = [||] |})
                    next
                    ctx
    }

let dailyFastbreakRouter database =
    router {
        get "/today" (getDailyFastbreakHandler database)
        get "/yesterday" (getYesterdaysFastbreakHandler database)
        getf "/day/%s" (fun day -> getFastbreakHandler database day)
    }
