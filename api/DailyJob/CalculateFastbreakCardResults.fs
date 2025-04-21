module api.DailyJob.CalculateFastbreakCardResults

open MongoDB.Driver
open api.Entities.EmptyFastbreakCard
open api.Entities.FastbreakSelections


let calculateResults
    (correctAnswers: System.Collections.Generic.IDictionary<string, EmptyFastbreakCardItem>)
    (selectionStates: FastbreakSelectionState seq)
    =
    selectionStates
    |> Seq.map (fun state ->
        // Process each selection in the state
        let correctSelections, incorrectSelections =
            state.selections
            |> Array.partition (fun selection ->
                match correctAnswers.TryGetValue(selection._id) with
                | true, item -> selection.userAnswer = item.correctAnswer
                | false, _ -> false)

        // Calculate totals
        let totalCorrect = correctSelections.Length
        let totalIncorrect = incorrectSelections.Length
        let totalPoints = correctSelections |> Array.sumBy (fun s -> s.points)

        // Extract IDs for correct and incorrect selections
        let correctIds = correctSelections |> Array.map (fun s -> s._id)
        let incorrectIds = incorrectSelections |> Array.map (fun s -> s._id)

        // Create the result
        let result =
            { totalPoints = totalPoints
              totalCorrect = totalCorrect
              totalIncorrect = totalIncorrect
              correct = correctIds
              incorrect = incorrectIds }

        // Return an updated state with the calculated results
        { state with results = Some result })

let toSelectionStateWriteModels (states: seq<FastbreakSelectionState>) : seq<WriteModel<FastbreakSelectionState>> =
    states
    |> Seq.map (fun state ->
        let filter =
            Builders<FastbreakSelectionState>.Filter
                .And(
                    Builders<FastbreakSelectionState>.Filter.Eq((fun s -> s.userId), state.userId),
                    Builders<FastbreakSelectionState>.Filter.Eq((fun s -> s.date), state.date)
                )

        let update = ReplaceOneModel(filter, state)
        update.IsUpsert <- true
        upcast update)

let calculateFastbreakCardResults (database: IMongoDatabase) =
    task {
        let lockedCardsCollection =
            database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

        let lockedCards =
            lockedCardsCollection
                .Find(Builders<FastbreakSelectionState>.Filter.Eq(_.results, None))
                .ToEnumerable()

        let cards = Seq.cast<FastbreakSelectionState> lockedCards
        
        for card in cards do
            let scheduleResults: System.Collections.Generic.IDictionary<string, EmptyFastbreakCardItem> =
                database
                    .GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")
                    .Find(fun x -> x.date = card.date)
                    .ToList()
                |> Seq.collect _.items
                |> Seq.map (fun item -> item.id, item) // Replace `item.id` with your actual key
                |> dict
                
            printf ($"Processed locked card for user {card.userId} and date {card.date}\n")

            let results = calculateResults scheduleResults cards |> Seq.toArray

            if results.Length > 0 then
                lockedCardsCollection.BulkWrite(toSelectionStateWriteModels results) |> ignore
            
            


        ""
    }
