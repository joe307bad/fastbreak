module Fastbreak.Daily.V2Main

open System.Diagnostics
open Fastbreak.Daily.ChartDownloader
open Fastbreak.Daily.V2NarrativeGenerator

let private timeAsync name (work: Async<'a>) = async {
    let sw = Stopwatch.StartNew()
    let! result = work
    sw.Stop()
    printfn "[Timer] %s: %.2fs" name sw.Elapsed.TotalSeconds
    return result
}

let run () = async {
    let totalTimer = Stopwatch.StartNew()

    let! charts = timeAsync "Download charts" (downloadCharts())

    let! topics = timeAsync "Generate V2 topics" (generate charts)

    totalTimer.Stop()
    printfn ""
    printfn "========================================="
    printfn "[Timer] V2 TOTAL: %.2fs (%.1f minutes)" totalTimer.Elapsed.TotalSeconds (totalTimer.Elapsed.TotalMinutes)
    printfn "========================================="

    return topics
}
