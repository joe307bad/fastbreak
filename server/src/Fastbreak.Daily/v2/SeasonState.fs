module Fastbreak.Daily.V2.SeasonState

open System

type League =
    | NFL
    | NBA
    | MLB
    | NHL

type SeasonState =
    | InSeason
    | OffSeasonPreDraft
    | OffSeasonPostDraft
    | PreSeason
    | PostSeason
    | Championship
    | PostChampionship

let stateToString =
    function
    | InSeason -> "IN-SEASON"
    | OffSeasonPreDraft -> "OFF-SEASON-PRE-DRAFT"
    | OffSeasonPostDraft -> "OFF-SEASON-POST-DRAFT"
    | PreSeason -> "PRE-SEASON"
    | PostSeason -> "POST-SEASON"
    | Championship -> "CHAMPIONSHIP"
    | PostChampionship -> "POST-CHAMPIONSHIP"

let leagueToString =
    function
    | NFL -> "NFL"
    | NBA -> "NBA"
    | MLB -> "MLB"
    | NHL -> "NHL"

// NFL: preseason in Aug, regular season Sep–early Jan, playoffs mid-Jan–early Feb,
// Super Bowl ~2nd Sunday of Feb, draft late April.
let private nflState (date: DateTime) =
    match date.Month, date.Day with
    | 8, _ -> PreSeason
    | 9, _ -> InSeason
    | (10 | 11 | 12), _ -> InSeason
    | 1, d when d <= 7 -> InSeason
    | 1, _ -> PostSeason
    | 2, d when d <= 14 -> Championship
    | 2, d when d <= 21 -> PostChampionship
    | 2, _ -> OffSeasonPreDraft
    | 3, _ -> OffSeasonPreDraft
    | 4, d when d < 24 -> OffSeasonPreDraft
    | 4, _ -> OffSeasonPostDraft
    | (5 | 6 | 7), _ -> OffSeasonPostDraft
    | _ -> OffSeasonPreDraft

// NBA: preseason early Oct, regular season ~Oct 22–mid-April, playoffs mid-April–mid-June,
// Finals early-mid June, draft late June.
let private nbaState (date: DateTime) =
    match date.Month, date.Day with
    | 10, d when d < 18 -> PreSeason
    | 10, _ -> InSeason
    | (11 | 12), _ -> InSeason
    | (1 | 2 | 3), _ -> InSeason
    | 4, d when d < 15 -> InSeason
    | 4, _ -> PostSeason
    | 5, _ -> PostSeason
    | 6, d when d < 20 -> Championship
    | 6, d when d < 27 -> PostChampionship
    | 6, _ -> OffSeasonPreDraft
    | (7 | 8 | 9), _ -> OffSeasonPostDraft
    | _ -> OffSeasonPostDraft

// MLB: spring training mid-Feb, regular season late March–late Sept,
// playoffs Oct, World Series late Oct–early Nov. Draft is mid-July (in-season),
// so the off-season is treated as pre-draft until the next year's draft window.
let private mlbState (date: DateTime) =
    match date.Month, date.Day with
    | 2, d when d < 15 -> OffSeasonPreDraft
    | 2, _ -> PreSeason
    | 3, d when d < 27 -> PreSeason
    | 3, _ -> InSeason
    | (4 | 5 | 6), _ -> InSeason
    | 7, _ -> InSeason
    | (8 | 9), _ -> InSeason
    | 10, d when d < 25 -> PostSeason
    | 10, _ -> Championship
    | 11, d when d <= 5 -> Championship
    | 11, d when d <= 12 -> PostChampionship
    | 11, _ -> OffSeasonPreDraft
    | (12 | 1), _ -> OffSeasonPreDraft
    | _ -> OffSeasonPreDraft

// NHL: preseason late Sept, regular season early Oct–mid-April, playoffs mid-April–mid-June,
// Stanley Cup Final June, draft late June.
let private nhlState (date: DateTime) =
    match date.Month, date.Day with
    | 9, d when d < 20 -> OffSeasonPostDraft
    | 9, _ -> PreSeason
    | 10, d when d < 8 -> PreSeason
    | 10, _ -> InSeason
    | (11 | 12 | 1 | 2 | 3), _ -> InSeason
    | 4, d when d < 18 -> InSeason
    | 4, _ -> PostSeason
    | 5, _ -> PostSeason
    | 6, d when d < 25 -> Championship
    | 6, _ -> PostChampionship
    | 7, d when d <= 1 -> PostChampionship
    | (7 | 8), _ -> OffSeasonPostDraft
    | _ -> OffSeasonPostDraft

// Lower number = higher priority (sorted to the top of the list).
let private statePriority =
    function
    | Championship -> 1
    | PostSeason -> 2
    | PostChampionship -> 3
    | InSeason -> 4
    | PreSeason -> 5
    | OffSeasonPostDraft -> 6
    | OffSeasonPreDraft -> 7

type LeagueState = {
    League: string
    State: string
    Priority: int
}

let getSeasonStates (date: DateTime) : LeagueState list =
    [ NFL, nflState date
      NBA, nbaState date
      MLB, mlbState date
      NHL, nhlState date ]
    |> List.map (fun (league, state) ->
        { League = leagueToString league
          State = stateToString state
          Priority = statePriority state })
    |> List.sortBy (fun ls -> ls.Priority)
