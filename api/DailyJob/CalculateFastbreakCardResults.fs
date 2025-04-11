module api.DailyJob.CalculateFastbreakCardResults

open MongoDB.Driver
open Shared
open api.Entities.EmptyFastbreakCard


let calculateResults (correctAnswers: System.Collections.Generic.IDictionary<string, EmptyFastbreakCardItem>) (selectionStates: FastbreakSelectionState seq) =
    selectionStates |> Seq.map (fun state ->
        // Process each selection in the state
        let correctSelections, incorrectSelections =
            state.selections
            |> Array.partition (fun selection ->
                match correctAnswers.TryGetValue(selection.id) with
                | true, item -> selection.userAnswer = item.correctAnswer
                | false, _ -> false)
        
        // Calculate totals
        let totalCorrect = correctSelections.Length
        let totalIncorrect = incorrectSelections.Length
        let totalPoints = correctSelections |> Array.sumBy (fun s -> s.points)
        
        // Extract IDs for correct and incorrect selections
        let correctIds = correctSelections |> Array.map (fun s -> s.id)
        let incorrectIds = incorrectSelections |> Array.map (fun s -> s.id)
        
        // Create the result
        let result = 
            { totalPoints = totalPoints
              totalCorrect = totalCorrect
              totalIncorrect = totalIncorrect
              correct = correctIds
              incorrect = incorrectIds }
        
        // Return an updated state with the calculated results
        { state with results = result }
    )
let calculateFastbreakCardResults (database: IMongoDatabase, yesterday, today, tomorrow) =
    task {
        let date = yesterday;

        let scheduleResults: System.Collections.Generic.IDictionary<string, EmptyFastbreakCardItem> =
            database.GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")
                .Find(fun x -> x.date = date)
                .ToList()
                |> Seq.collect (fun card -> card.items)
                |> Seq.map (fun item -> item.id, item) // Replace `item.id` with your actual key
                |> dict

        let lockedCards =
            database
                .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                .Find(Builders<FastbreakSelectionState>.Filter.Eq(_.date, date))
                .ToEnumerable()

        let cards = Seq.cast<FastbreakSelectionState> lockedCards

        printf ($"Locked cards found for {yesterday}: {cards |> Seq.length}\n")

        let results = calculateResults scheduleResults cards
        for result in results do
            let correct = String.concat ", " result.results.correct
            let incorrect = String.concat ", " result.results.incorrect
            printf $"---- Card ID: {result.cardId}\n"
            printf $"Incorrect: {incorrect}\n"
            printf $"Correct: {correct}\n"
            printf $"Points: {result.results.totalPoints}\n"
            printf $"-------- /\n"
            ()

        ""
    }
