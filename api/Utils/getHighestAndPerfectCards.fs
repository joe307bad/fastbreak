module api.Utils.getHighestAndPerfectCards

open api.Entities.StatSheet
open api.Entities.FastbreakSelections

let getHighestAndPerfectCards (fastbreakSelections: FastbreakSelectionState list) (statSheet: StatSheet option) =
    let createCardFromSelection (selection: FastbreakSelectionState) =
        { date = selection.date; points = selection.results.Value.totalPoints }
        
    let highestFromSelections =
        fastbreakSelections
        |> List.filter _.results.IsSome
        |> List.map createCardFromSelection
        |> List.sortByDescending _.points
        |> List.tryHead
        
    let perfectFromSelections =
        fastbreakSelections
        |> List.filter (fun s -> 
            s.results.IsSome && 
            s.totalPoints = s.results.Value.totalPoints)
        |> List.map createCardFromSelection
        |> List.distinctBy _.date
        
    match statSheet, highestFromSelections with
    | None, None -> 
        { date = ""; points = 0 }, { cards = [||]; highest = { date = ""; points = 0 } }
        
    | None, Some highest -> 
        highest, 
        { cards = perfectFromSelections |> List.toArray
          highest = highest }
          
    | Some sheet, None -> 
        sheet.items.highestFastbreakCardEver,
        sheet.items.perfectFastbreakCards
        
    | Some sheet, Some highest ->
        let highestCard = 
            if highest.points > sheet.items.highestFastbreakCardEver.points then
                highest
            else
                sheet.items.highestFastbreakCardEver
                
        let allPerfectCards =
            (sheet.items.perfectFastbreakCards.cards |> Array.toList) @ perfectFromSelections
            |> List.distinctBy _.date
            |> List.toArray
            
        let perfectCards = 
            { cards = allPerfectCards
              highest = 
                  if allPerfectCards.Length > 0 then
                      allPerfectCards 
                      |> Array.maxBy _.points
                  else
                      { date = ""; points = 0 } }
                
        highestCard, perfectCards