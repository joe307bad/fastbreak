module Fastbreak.Daily.V2.TopicMapper

open Fastbreak.Daily.V2.SeasonState

type Topic = {
    Category: string
    Description: string
}

type LeagueTopics = {
    League: string
    State: string
    Priority: int
    Count: int
    Topics: Topic list
}

let private topicsForState (state: string) : int * Topic list =
    match state with
    | "IN-SEASON" ->
        3, [
            { Category = "RECAP"; Description = "The most exciting game recap rich with statistics" }
            { Category = "INJURY REPORT"; Description = "A team's injury report with statistical impact" }
            { Category = "POWER RANKING"; Description = "Top 3 or bottom 3 power rankings with statistics" }
        ]
    | "OFF-SEASON-PRE-DRAFT" ->
        1, [
            { Category = "DRAFT"; Description = "Top draft prospects" }
            { Category = "TRANSACTION"; Description = "Team transactions" }
            { Category = "FREE AGENCY"; Description = "Free agency transactions" }
        ]
    | "OFF-SEASON-POST-DRAFT" ->
        1, [
            { Category = "DRAFT"; Description = "Draft grades with impactful draft picks" }
            { Category = "TRANSACTION"; Description = "Rookie signings" }
            { Category = "TRANSACTION"; Description = "Roster moves" }
        ]
    | "PRE-SEASON" ->
        1, [
            { Category = "PRESEASON"; Description = "Training Camp" }
            { Category = "PRESEASON"; Description = "Roster Battles" }
            { Category = "PRESEASON"; Description = "Preseason games with statistics" }
            { Category = "PRESEASON"; Description = "Preason power rankings" }
        ]
    | "POST-SEASON" ->
        2, [
            { Category = "PLAYOFFS"; Description = "Playoff matchups with statistics" }
            { Category = "PLAYOFFS"; Description = "Series predictions" }
        ]
    | "CHAMPIONSHIP" ->
        2, [
            { Category = "CHAMPIONSHIP"; Description = "Finals predictions with statistics" }
            { Category = "CHAMPIONSHIP"; Description = "Championship odds" }
        ]
    | "POST-CHAMPIONSHIP" ->
        1, [
            { Category = "CHAMPIONSHIP"; Description = "Championship recap with statistical impact" }
        ]
    | other ->
        0, [ { Category = "UNKNOWN"; Description = sprintf "unknown state: %s" other } ]

let convertSeasonStateToTopic (states: LeagueState list) : LeagueTopics list =
    states
    |> List.map (fun ls ->
        let count, topics = topicsForState ls.State
        { League = ls.League
          State = ls.State
          Priority = ls.Priority
          Count = count
          Topics = topics })
