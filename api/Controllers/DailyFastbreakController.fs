module DailyFastbreakController

open System
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
      fastbreakCard: EmptyFastbreakCardItem[]
      lockedCardForUser: FastbreakSelectionState option }

let getDailyFastbreakHandler (database: IMongoDatabase) (next: HttpFunc) (ctx: HttpContext) =
    task {
        let collection = database.GetCollection<EmptyFastBreakCard>("empty-fastbreak-cards")
        let today = DateTime.Now.ToString("yyyyMMdd")

        let filter = Builders<EmptyFastBreakCard>.Filter.Eq((fun x -> x.date), today)

        let! card =
            collection.Find(filter).FirstOrDefaultAsync()
            |> Async.AwaitTask
            |> asyncMap Option.ofObj

        let lockedCardForUserAsync =
            match ctx.TryGetQueryStringValue "userId" with
            | Some id ->
                let lockedCards =
                    database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

                let f =
                    Builders<FastbreakSelectionState>.Filter
                        .And(
                            Builders<FastbreakSelectionState>.Filter.Eq(_.userId, id),
                            Builders<FastbreakSelectionState>.Filter.Eq(_.date, today)
                        )

                async {
                    try
                        let! result = lockedCards.Find(f).FirstOrDefaultAsync() |> Async.AwaitTask

                        return
                            if obj.ReferenceEquals(result, null) then
                                None
                            else
                                Some result
                    with _ ->
                        return None
                }
            | None -> async { return Option<FastbreakSelectionState>.None }

        match card with
        | None -> return! json Seq.empty next ctx
        | Some card ->
            return!
                json
                    ({| fastbreakCard = card.items
                        leaderboard = [||]
                        lockedCardForUser =
                         match lockedCardForUserAsync |> Async.RunSynchronously with
                         | Some v -> Nullable(v)
                         | None -> Nullable() |})
                    next
                    ctx
    }

let dailyFastbreakRouter database =
    router { get "/daily" (getDailyFastbreakHandler database) }
