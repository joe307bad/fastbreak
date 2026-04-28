module Fastbreak.Daily.V2.PromptBuilder

open System
open Fastbreak.Daily.V2.TopicMapper

type Prompt = {
    League: string
    Text: string
}

let private buildTopicLine (executionDate: DateTime) (league: string) (category: string) (description: string) =
    let cutoff = executionDate.AddDays(-3.0).ToString("yyyy-MM-dd")
    sprintf "Generate a 3 sentence hard hitting news summary for %s %s involving %s. All information should be factual, grounded in real sources. Aggressively prioritize recent news for today (%s) but make sure any sources are no older than %s. (league: %s, category: %s)"
        league
        (category.ToLower())
        (description.ToLower())
        (executionDate.ToString("yyyy-MM-dd"))
        cutoff
        league
        category

let private header =
    "follow the instructions below and don't repeat yourself at all. Each summary should have unique information. Each summary should be as concise as possible and avoid verbosity. Each summary should focus on one matchup, a couple of teams, or a couple of players. Avoid trying to talk about too many teams or players in a single summary"

let private footer =
    """Respond with valid JSON only — a single array of objects, one object per bullet above in the same order. Each object must have exactly these three string fields: "league", "summary", and "category". The "league" and "category" fields MUST be copied verbatim from the parenthetical at the end of that bullet (e.g. a bullet ending with "(league: NHL, category: PLAYOFFS)" produces an object with "league": "NHL" and "category": "PLAYOFFS"). Never set "league" or "category" to null, empty, or a placeholder — always use the exact values from the parenthetical. Output only the JSON array, no surrounding prose, no code fences."""

let buildPrompts (executionDate: DateTime) (topicsByLeague: LeagueTopics list) : Prompt list =
    let rng = Random()
    topicsByLeague
    |> List.map (fun lt ->
        let shuffled = lt.Topics |> List.sortBy (fun _ -> rng.Next())
        let take = min lt.Count (List.length shuffled)
        let bullets =
            shuffled
            |> List.truncate take
            |> List.map (fun t ->
                "- " + buildTopicLine executionDate lt.League t.Category t.Description)
            |> String.concat "\n"
        { League = lt.League
          Text = header + "\n" + bullets + "\n\n" + footer })
