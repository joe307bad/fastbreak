module ScheduleController

open System
open Giraffe
open Microsoft.AspNetCore.Http
open MongoDB.Driver
open Saturn.Endpoint
open ScheduleEntity

type Game =
    {
      id: string
      homeTeam: string
      awayTeam: string
      location: string
      homeTeamLogo: string
      awayTeamLogo: string
      league: string
      date: string }

let getTomorrowsSchedulesHandler (database: IMongoDatabase) : HttpHandler =
    fun (next: HttpFunc) (ctx: HttpContext) ->
        task {
            let! schedules =
                async {
                    let collection = database.GetCollection<Schedule>("schedules")
                    let tomorrow = DateTime.Now.AddDays(1).ToString("yyyyMMdd")
                    let filter = Builders<Schedule>.Filter.Eq((fun x -> x.date), tomorrow)
                    let! results = collection.Find(filter).ToListAsync() |> Async.AwaitTask

                    return
                        results
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

                                    { id = event.id
                                      homeTeam = homeTeam.displayName
                                      awayTeam = awayTeam.displayName
                                      location =
                                        competition.venue
                                        |> Option.map (fun obj -> obj.fullName)
                                        |> Option.defaultValue null
                                      homeTeamLogo = homeTeam.logo
                                      awayTeamLogo = awayTeam.logo
                                      league = schedule.league
                                      date = event.date })))
                        |> Seq.toList
                }
                |> Async.StartAsTask

            return! json schedules next ctx
        }

let scheduleController database =
    router { get "/tomorrow" (getTomorrowsSchedulesHandler database) }
