module api.Utils.getHighestAndPerfectCards

open api.Entities.StatSheet
open api.Entities.FastbreakSelections

let getHighestAndPerfectCards (fastbreakSelections: FastbreakSelectionState list) (statSheet: StatSheet option) =
    // Helper to create a FastbreakCard from a selection state
    let createCardFromSelection (selection: FastbreakSelectionState) =
        { date = selection.date; points = selection.results.Value.totalPoints }
        
    // Find the highest card from the current selections
    let highestFromSelections =
        fastbreakSelections
        |> List.filter (fun s -> s.results.IsSome)
        |> List.map createCardFromSelection
        |> List.sortByDescending (fun card -> card.points)
        |> List.tryHead
        
    // Find perfect cards (where totalPoints equals results.totalPoints)
    let perfectFromSelections =
        fastbreakSelections
        |> List.filter (fun s -> 
            s.results.IsSome && 
            s.totalPoints = s.results.Value.totalPoints)
        |> List.map createCardFromSelection
        |> List.distinctBy (fun card -> card.date)
        
    match statSheet, highestFromSelections with
    | None, None -> 
        // No stat sheet and no valid selections
        { date = ""; points = 0 }, { cards = [||]; highest = { date = ""; points = 0 } }
        
    | None, Some highest -> 
        // No stat sheet but we have valid selections
        highest, 
        { cards = perfectFromSelections |> List.toArray
          highest = highest }
          
    | Some sheet, None -> 
        // We have a stat sheet but no valid selections
        sheet.items.highestFastbreakCardEver,
        sheet.items.perfectFastbreakCards
        
    | Some sheet, Some highest ->
        // We have both stat sheet and valid selections
        let highestCard = 
            if highest.points > sheet.items.highestFastbreakCardEver.points then
                highest
            else
                sheet.items.highestFastbreakCardEver
                
        // Combine perfect cards from the sheet with new ones, ensuring uniqueness by date
        let allPerfectCards =
            (sheet.items.perfectFastbreakCards.cards |> Array.toList) @ perfectFromSelections
            |> List.distinctBy (fun card -> card.date)
            |> List.toArray
            
        let perfectCards = 
            { cards = allPerfectCards
              highest = 
                  if allPerfectCards.Length > 0 then
                      allPerfectCards 
                      |> Array.maxBy (fun card -> card.points)
                  else
                      { date = ""; points = 0 } }
                
        highestCard, perfectCards