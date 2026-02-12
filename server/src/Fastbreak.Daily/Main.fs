module Fastbreak.Daily.Main

open Fastbreak.Daily.ChartDownloader
open Fastbreak.Daily.NarrativeGenerator

let run () = async {
    // Step 1: Download all chart data
    let! charts = downloadCharts()

    // Step 2: Generate narratives using chart data
    let! narratives = generate charts

    return narratives
}
