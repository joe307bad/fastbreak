namespace Fastbreak.Shared.Utils

module GetHighestAndPerfectCards =

    open Fastbreak.Shared.Entities

    let getHighestAndPerfectCards (fastbreakSelections: FastbreakSelectionState list) (statSheet: StatSheet option) =
        let createCardFromSelection (selection: FastbreakSelectionState) =
            { date = selection.date
              points = selection.results.Value.totalPoints }

        let highestFromSelections =
            fastbreakSelections
            |> List.filter _.results.IsSome
            |> List.map createCardFromSelection
            |> List.sortByDescending _.points
            |> List.tryHead

        let perfectFromSelections =
            fastbreakSelections
            |> List.filter (fun s -> s.results.IsSome && s.totalPoints = s.results.Value.totalPoints)
            |> List.map createCardFromSelection
            |> List.distinctBy _.date

        match statSheet, highestFromSelections with
        | None, None -> { date = ""; points = 0 }, [||]

        | None, Some highest -> highest, perfectFromSelections |> List.toArray

        | Some sheet, None -> sheet.items.highestFastbreakCardEver, sheet.items.perfectFastbreakCards

        | Some sheet, Some highest ->
            let highestCard =
                if highest.points > sheet.items.highestFastbreakCardEver.points then
                    highest
                else
                    sheet.items.highestFastbreakCardEver

            let allPerfectCards =
                (sheet.items.perfectFastbreakCards |> Array.toList) @ perfectFromSelections
                |> List.distinctBy _.date
                |> List.toArray

            highestCard, allPerfectCards
