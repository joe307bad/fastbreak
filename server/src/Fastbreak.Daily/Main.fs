module Fastbreak.Daily.Main

open System.Diagnostics
open Fastbreak.Daily.ChartDownloader
open Fastbreak.Daily.NarrativeGenerator
open Fastbreak.Daily.TextSegmentConverter

let private timeAsync name (work: Async<'a>) = async {
    let sw = Stopwatch.StartNew()
    let! result = work
    sw.Stop()
    printfn "[Timer] %s: %.2fs" name sw.Elapsed.TotalSeconds
    return result
}

let run () = async {
    let totalTimer = Stopwatch.StartNew()

    // Step 1: Download all chart data
    let! charts = timeAsync "Download charts" (downloadCharts())

    // Step 2: Generate narratives using chart data
    let! narratives = timeAsync "Generate narratives" (generate charts)

    // Step 3: Convert text to segments with links
    let! enrichedNarratives = timeAsync "Convert to segments" (enrichWithSegments narratives)

    totalTimer.Stop()
    printfn ""
    printfn "========================================="
    printfn "[Timer] TOTAL: %.2fs (%.1f minutes)" totalTimer.Elapsed.TotalSeconds (totalTimer.Elapsed.TotalMinutes)
    printfn "========================================="

    return enrichedNarratives
}
