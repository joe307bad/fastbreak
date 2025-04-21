module api.Utils.findHighestScoringCard

open api.Entities.FastbreakSelections
open api.Entities.StatSheet

let findHighestScoringCard (states: System.Collections.Generic.List<FastbreakSelectionState>) (card: FastbreakCard option) : FastbreakCard =
    // Define an empty card to use as fallback
    let emptyCard = { date = ""; points = 0 }
    
    if states.Count = 0 then
        // If we have no states to compare, return the original card or empty card if None
        match card with
        | Some c -> c
        | None -> emptyCard
    else
        // Start with the provided card as our baseline best card (if any)
        let mutable bestCard = 
            match card with
            | Some c -> c
            | None -> emptyCard
        
        // Iterate through each state and compare
        for state in states do
            // Only consider states that have results
            match state.results with
            | Some results when results.totalPoints > bestCard.points ->
                // We found a better result
                bestCard <- { date = state.date; points = results.totalPoints }
            | _ -> ()
                
        // Return the best card (either original, empty, or a new one from a state)
        bestCard