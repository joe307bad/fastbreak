module Fastbreak.Daily.V2.Main

open System
open Fastbreak.Daily.V2.SeasonState
open Fastbreak.Daily.V2.TopicMapper
open Fastbreak.Daily.V2.PromptBuilder
open Fastbreak.Daily.V2.TopicGenerator

let private buildTodaysPrompts () =
    let today = DateTime.UtcNow
    let states = getSeasonStates today

    printfn ""
    printfn "===== Season States (%s) =====" (today.ToString("yyyy-MM-dd"))
    states |> List.iter (fun ls ->
        printfn "  %-3s : %-22s (priority %d)" ls.League ls.State ls.Priority)
    printfn "===================================="

    let topics = convertSeasonStateToTopic states

    printfn ""
    printfn "===== Topics ====="
    topics |> List.iter (fun lt ->
        printfn "  %s [%s] (count: %d, priority: %d)" lt.League lt.State lt.Count lt.Priority
        lt.Topics |> List.iter (fun t ->
            printfn "    - [%s] %s" t.Category t.Description))
    printfn "=================="

    let prompts = buildPrompts today topics

    printfn ""
    printfn "===== Prompts (%d) =====" (List.length prompts)
    prompts |> List.iteri (fun i p ->
        printfn ""
        printfn "  --- [%d] %s ---" (i + 1) p.League
        printfn "%s" p.Text)
    printfn ""
    printfn "========================"

    prompts

// In-memory entry point: produce the generated topic list without touching disk.
let runService () = async {
    let prompts = buildTodaysPrompts ()
    let! generated = generateTopicsCore prompts
    return generated
}

// File-based entry point for the standalone `--v2` CLI subcommand.
let run (outputDir: string) = async {
    let prompts = buildTodaysPrompts ()
    let! generated = generateTopics outputDir prompts
    return generated
}
