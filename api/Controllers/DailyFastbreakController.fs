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

let getFastbreakHandler (database: IMongoDatabase) day (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")

        let dayDate = DateTime.ParseExact(day, "yyyyMMdd", null)

        let mondayDate =
            let daysToSubtract =
                match dayDate.DayOfWeek with
                | DayOfWeek.Monday -> 0
                | DayOfWeek.Sunday -> 6
                | _ -> int dayDate.DayOfWeek - 1

            dayDate.AddDays(float -daysToSubtract)

        let mondayId = mondayDate.ToString("yyyyMMdd")

        let leaderboard =
            database
                .GetCollection<Leaderboard>("leaderboards")
                .Find(Builders<Leaderboard>.Filter.Eq(_.id, mondayId))
                .FirstOrDefault()

        let filter = Builders<EmptyFastbreakCard>.Filter.Eq(_.date, day)

        let! card =
            collection.Find(filter).FirstOrDefaultAsync()
            |> Async.AwaitTask
            |> asyncMap Option.ofObj

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
                    ({| lastLockedCardResults = lastLockedCardResults
                        statSheetForUser = statSheetForUser
                        // TODO this should be a seperate request because it should be cached seperately from the rest of these properties
                        lockedCardForUser = lockedCard
                        fastbreakCard = card.items
                        leaderboard = leaderboardItems |})
                    next
                    ctx
    }

let dailyFastbreakRouter database =
    router { getf "/day/%s" (fun day -> getFastbreakHandler database day) }
