module api.Controllers.DailyFastbreakController

open System
open Fastbreak.Shared.Utils
open Giraffe
open Microsoft.AspNetCore.Http
open MongoDB.Driver
open Saturn.Endpoint
open Fastbreak.Shared.Entities
open Fastbreak.Shared.Utils.GetStatSheetForUser
open Fastbreak.Shared.Utils.LockedCardsForUser

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

let getFastbreakHandler (database: IMongoDatabase) day (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")

        let dayDate = DateTime.ParseExact(day, "yyyyMMdd", null)

        let sundayDate =
            let daysToSubtract =
                match dayDate.DayOfWeek with
                | DayOfWeek.Sunday -> 0
                | _ -> int dayDate.DayOfWeek

            dayDate.AddDays(float -daysToSubtract)

        let sundayId = sundayDate.ToString("yyyyMMdd")

        let leaderboard =
            database
                .GetCollection<Leaderboard>("leaderboards")
                .Find(Builders<Leaderboard>.Filter.Eq(_.id, sundayId))
                .FirstOrDefault()

        let filter = Builders<EmptyFastbreakCard>.Filter.Eq(_.date, day)

        let! card =
            collection.Find(filter).FirstOrDefaultAsync()
            |> Async.AwaitTask
            |> AsyncMap.asyncMap Option.ofObj

        let lockedCard =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id -> getLockedCardForUser database id day |> Option.ofObj
            | None -> None

        let lastLockedCardResults =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id -> getLastLockedCardResultsForUser database id |> Option.ofObj
            | None -> None

        let statSheetForUser =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id -> getStatSheetForUser database id |> Option.ofObj
            | None -> None

        let leaderboardItems =
            if box leaderboard |> isNull then
                null
            else
                box leaderboard.items

        match card with
        | None -> return! json Seq.empty next ctx
        | Some card ->
            return!
                json
                    {| lastLockedCardResults = lastLockedCardResults
                       statSheetForUser = statSheetForUser
                       lockedCardForUser = lockedCard
                       fastbreakCard = card.items
                       leaderboard = leaderboardItems |}
                    next
                    ctx
    }

let getScheduleHandler (database: IMongoDatabase) day (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")
        let filter = Builders<EmptyFastbreakCard>.Filter.Eq(_.date, day)

        let! card =
            collection.Find(filter).FirstOrDefaultAsync()
            |> Async.AwaitTask
            |> AsyncMap.asyncMap Option.ofObj

        match card with
        | None -> return! json Seq.empty next ctx
        | Some card ->
            return!
                json
                    {| fastbreakCard = card.items |}
                    next
                    ctx
    }

let getStatsHandler (database: IMongoDatabase) (day, userId) (next: HttpFunc) (ctx: HttpContext) =
    task {
        let dayDate = DateTime.ParseExact(day, "yyyyMMdd", null)

        let sundayDate =
            let daysToSubtract =
                match dayDate.DayOfWeek with
                | DayOfWeek.Sunday -> 0
                | _ -> int dayDate.DayOfWeek

            dayDate.AddDays(float -daysToSubtract)

        let sundayId = sundayDate.ToString("yyyyMMdd")

        let leaderboard =
            database
                .GetCollection<Leaderboard>("leaderboards")
                .Find(Builders<Leaderboard>.Filter.Eq(_.id, sundayId))
                .FirstOrDefault()

        let previousDayDate = dayDate.AddDays(-1.0)
        let previousDay = previousDayDate.ToString("yyyyMMdd")
        
        let statSheetForUser = getStatSheetForUser database userId |> Option.ofObj

        let leaderboardItems =
            if box leaderboard |> isNull then
                null
            else
                box leaderboard.items
                
        let lockedCardForDate = 
            let collection: IMongoCollection<FastbreakSelectionState> =
                database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

            let filter =
                Builders<FastbreakSelectionState>.Filter
                    .And(
                        Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId),
                        Builders<FastbreakSelectionState>.Filter.Eq(_.date, day)
                    )
            collection.Find(filter).Limit(1).ToListAsync()
            |> Async.AwaitTask
            |> Async.RunSynchronously
            |> Seq.tryHead

        return!
            json
                {| weeklyLeaderboard = leaderboardItems
                   statSheetForUser = statSheetForUser
                   weekStartDate = sundayId
                   lockedCardForDate = lockedCardForDate
                   requestedDate = day
                   previousDay = previousDay |}
                next
                ctx
    }

let dailyFastbreakRouter database =
    router { 
        getf "/day/%s" (getFastbreakHandler database)
        getf "/day/%s/schedule" (getScheduleHandler database)
        getf "/day/%s/stats/%s" (getStatsHandler database)
    }
