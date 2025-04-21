module api.Utils.getPerfectFastbreakCards

open api.Entities.FastbreakSelections
open api.Entities.StatSheet

let getPerfectFastbreakCards 
    (selections: System.Collections.Generic.List<FastbreakSelectionState>) 
    (perfectCards: PerfectFastbreakCards option) : PerfectFastbreakCards =
    
    // Handle the option case - create empty structure if None
    let initialPerfectCards = 
        match perfectCards with
        | Some cards -> cards
        | None -> { cards = [||]; highest = { date = ""; points = 0 } }
    
    // Filter selections where total points equals results.totalPoints
    let newPerfectCards = 
        [for i in 0 .. selections.Count - 1 do
            let state = selections.[i]
            match state.results with
            | Some results when state.totalPoints = results.totalPoints ->
                yield { date = state.date; points = state.totalPoints }
            | _ -> ()]
    
    // Merge with existing perfect cards
    let allPerfectCards = 
        Array.append initialPerfectCards.cards (Array.ofList newPerfectCards)
    
    // Find card with highest points
    let highestCard =
        if Array.isEmpty allPerfectCards then
            // If no cards, use the default highest
            initialPerfectCards.highest
        else
            allPerfectCards
            |> Array.maxBy (fun card -> card.points)
    
    // Return new PerfectFastbreakCards
    { cards = allPerfectCards; highest = highestCard }