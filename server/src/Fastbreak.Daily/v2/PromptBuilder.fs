module Fastbreak.Daily.V2.PromptBuilder

open System
open Fastbreak.Daily.V2.TopicMapper

type Prompt = {
    League: string
    Text: string
    // The category for each bullet, in the same order as the bullets appear
    // in `Text`. The generator uses this to restore the category server-side
    // when Gemini occasionally drops it from the response JSON.
    Categories: string list
}

let private buildTopicLine (league: string) (category: string) (description: string) =
    sprintf "Generate a 3 sentence hard hitting news summary for %s %s involving %s. All information must be factual, grounded in real sources, and reflect the LATEST state of events as of TODAY (see header). (league: %s, category: %s)"
        league
        (category.ToLower())
        (description.ToLower())
        league
        category

let private buildHeader (executionDate: DateTime) =
    let today = executionDate.ToString("yyyy-MM-dd")
    let yest = executionDate.AddDays(-1.0).ToString("yyyy-MM-dd")
    sprintf """TODAY IS %s. You MUST treat this as the current date.

Recency rules — these override every other instruction:
- Use Google Search grounding to find the most recent reporting. Prefer sources published on %s or %s. Reject any source older than %s when a newer one exists.
- Before writing each summary, verify the CURRENT state of the matchup, series, standings, or storyline. If a playoff series has ended, a team has been eliminated, a player has been traded/signed/released, a game has finished, or any prior storyline has been superseded, the summary MUST reflect the newer event — never describe a superseded state as still ongoing.
- When you are tempted to describe an in-progress series or push, ALWAYS confirm the most recent game date and series record first. If the series is over, lead with the result, not the lead-up.
- If grounded search returns conflicting information, trust the most recently published source.
- If you cannot find a source from %s or %s for a given topic, write a summary that explicitly notes "as of [most recent source date]" using the actual date of the latest source you found, rather than fabricating current-day activity.

Style rules:
- Don't repeat yourself across summaries — each one must contain unique information.
- Be concise. Avoid verbosity, hedging, and recap of background context.
- Focus each summary on one matchup, a couple of teams, or a couple of players. Don't try to cover too many subjects in a single summary."""
        today
        today
        yest
        yest
        today
        yest

let private footer =
    """Respond with valid JSON only — a single array of objects, one object per bullet above in the same order. Each object must have exactly these three string fields: "league", "summary", and "category". The "league" and "category" fields MUST be copied verbatim from the parenthetical at the end of that bullet (e.g. a bullet ending with "(league: NHL, category: PLAYOFFS)" produces an object with "league": "NHL" and "category": "PLAYOFFS"). Never set "league" or "category" to null, empty, or a placeholder — always use the exact values from the parenthetical. Output only the JSON array, no surrounding prose, no code fences."""

let buildPrompts (executionDate: DateTime) (topicsByLeague: LeagueTopics list) : Prompt list =
    let rng = Random()
    let header = buildHeader executionDate
    topicsByLeague
    |> List.map (fun lt ->
        let shuffled = lt.Topics |> List.sortBy (fun _ -> rng.Next())
        let take = min lt.Count (List.length shuffled)
        let chosen = shuffled |> List.truncate take
        let bullets =
            chosen
            |> List.map (fun t ->
                "- " + buildTopicLine lt.League t.Category t.Description)
            |> String.concat "\n"
        { League = lt.League
          Text = header + "\n\n" + bullets + "\n\n" + footer
          Categories = chosen |> List.map (fun t -> t.Category) })
