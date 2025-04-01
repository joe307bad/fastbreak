module DailyFastbreakController

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

let getTomorrowsSchedulesHandler (database: IMongoDatabase) =
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

            return schedules
        }

// Define the record to match the JSON object structure
type ScheduleResponse = {
    card: string
    leaderboard: string
    statSheet: string
    week: int
    season: int
    day: int
}

[<CLIMutable>]
type LeaderboardItem = {
    id: string
    user: string
    points: int
}

[<CLIMutable>]
type EmptyFastbreakCardItem = {
    id: string
    ``type``: string
    homeTeam: string
    homeTeamSubtitle: string
    awayTeam: string
    awayTeamSubtitle: string
    dateLine1: string
    dateLine2: string
    dateLine3: string
    points: int
    question: string
    answer1: string
    answer2: string
    answer3: string
    answer4: string
    correctAnswer: string
}

[<CLIMutable>]
type DailyFastbreak = {
    leaderboard: LeaderboardItem list
    fastbreakCard: EmptyFastbreakCardItem list
}

let sampleDailyFastbreak = {
    leaderboard = [
        { 
            id = "user123"
            user = "BasketballFan42"
            points = 280 
        }
        { 
            id = "user456"
            user = "HoopsExpert"
            points = 265 
        }
        { 
            id = "user789"
            user = "CourtVision"
            points = 245 
        }
        { 
            id = "user101"
            user = "SlamDunkQueen"
            points = 230 
        }
        { 
            id = "user202"
            user = "BenchWarmer24"
            points = 198 
        }
    ]
    fastbreakCard = [
        { 
            id = "game001"
            ``type`` = "featured-pick-em"
            homeTeam = "Boston Celtics"
            homeTeamSubtitle = "51-16"
            awayTeam = "Los Angeles Lakers"
            awayTeamSubtitle = "36-32"
            dateLine1 = "Today, 7:30 PM ET"
            dateLine2 = "TD Garden"
            dateLine3 = "National TV: ESPN"
            points = 100
            question = "Who will win tonight's featured matchup?"
            answer1 = "Boston Celtics"
            answer2 = "Los Angeles Lakers"
            answer3 = null
            answer4 = null
            correctAnswer = null
        }
        {
            id = "game002"
            ``type`` = "pick-em"
            homeTeam = "Phoenix Suns"
            homeTeamSubtitle = "42-26"
            awayTeam = "Denver Nuggets"
            awayTeamSubtitle = "47-21"
            dateLine1 = "Tomorrow, 9:00 PM ET"
            dateLine2 = "Footprint Center"
            dateLine3 = null
            points = 75
            question = "Who will win tomorrow's game?"
            answer1 = "Phoenix Suns"
            answer2 = "Denver Nuggets"
            answer3 = null
            answer4 = null
            correctAnswer = null
        }
        {
            id = "trivia001"
            ``type`` = "trivia-multiple-choice"
            homeTeam = null
            homeTeamSubtitle = null
            awayTeam = null
            awayTeamSubtitle = null
            dateLine1 = "NBA Trivia"
            dateLine2 = null
            dateLine3 = null
            points = 50
            question = "Which player holds the record for most 3-pointers in a single season?"
            answer1 = "Stephen Curry"
            answer2 = "Ray Allen"
            answer3 = "James Harden"
            answer4 = "Klay Thompson"
            correctAnswer = "Stephen Curry"
        }
        {
            id = "trivia002"
            ``type`` = "trivia-tf"
            homeTeam = null
            homeTeamSubtitle = null
            awayTeam = null
            awayTeamSubtitle = null
            dateLine1 = "Basketball True/False"
            dateLine2 = null
            dateLine3 = null
            points = 25
            question = "LeBron James has won exactly 4 NBA championships in his career."
            answer1 = "True"
            answer2 = "False"
            answer3 = null
            answer4 = null
            correctAnswer = "True"
        }
    ]
}

// let getTomorrowsSchedule database =
//     getTomorrowsSchedulesHandler database

let getDailyFastbreakHandler schedules (next: HttpFunc) (ctx: HttpContext) =
    json sampleDailyFastbreak next ctx

let dailyFastbreakController database =
    let schedules = 
        async {
            let! gamesList = Async.AwaitTask (getTomorrowsSchedulesHandler database)
            return gamesList
        } |> Async.RunSynchronously

    router {
        get "/" (getDailyFastbreakHandler schedules)
    }