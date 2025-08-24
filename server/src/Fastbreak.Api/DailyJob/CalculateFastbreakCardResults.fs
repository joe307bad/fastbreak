module api.DailyJob.CalculateFastbreakCardResults

open System
open MongoDB.Driver
open Fastbreak.Shared.Entities


let calculateResults
    (correctAnswers: System.Collections.Generic.IDictionary<string, EmptyFastbreakCardItem>)
    (selectionStates: FastbreakSelectionState seq)
    =
    
    selectionStates
    |> Seq.map (fun state ->
        let selections = if isNull state.selections then [||] else state.selections
        
        let correctSelections, incorrectSelections =
            selections
            |> Array.partition (fun selection ->
                match correctAnswers.TryGetValue(selection._id) with
                | true, item -> selection.userAnswer = item.correctAnswer
                | false, _ -> false)

        let totalCorrect = correctSelections.Length
        let totalIncorrect = incorrectSelections.Length
        let totalPoints = correctSelections |> Array.sumBy _.points

        let correctIds = correctSelections |> Array.map _._id
        let incorrectIds = incorrectSelections |> Array.map _._id

        let result =
            { totalPoints = totalPoints
              totalCorrect = totalCorrect
              totalIncorrect = totalIncorrect
              correct = correctIds
              incorrect = incorrectIds
              date = state.date }

        { state with results = Some result })

let toSelectionStateWriteModels (states: seq<FastbreakSelectionState>) : seq<WriteModel<FastbreakSelectionState>> =
    states
    |> Seq.map (fun state ->
        let filter =
            Builders<FastbreakSelectionState>.Filter
                .And(
                    Builders<FastbreakSelectionState>.Filter.Eq(_.userId, state.userId),
                    Builders<FastbreakSelectionState>.Filter.Eq(_.date, state.date)
                )

        let update = ReplaceOneModel(filter, state)
        update.IsUpsert <- true
        upcast update)

let calculateFastbreakCardResults (database: IMongoDatabase) =
    task {
        let lockedCardsCollection =
            database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

        let lockedCards =
            
            let easternTimeZone = TimeZoneInfo.FindSystemTimeZoneById("America/New_York")
            let today = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, easternTimeZone).Date.ToString("yyyyMMdd")

            let filter =
                Builders<FastbreakSelectionState>.Filter
                    .And(
                        Builders<FastbreakSelectionState>.Filter.Eq(_.results, None),
                        Builders<FastbreakSelectionState>.Filter.Lt(_.date, today)
                    )

            lockedCardsCollection.Find(filter).ToEnumerable()

        let cards = Seq.cast<FastbreakSelectionState> lockedCards

        for card in cards do
            let scheduleResults: System.Collections.Generic.IDictionary<string, EmptyFastbreakCardItem> =
                database
                    .GetCollection<EmptyFastbreakCard>("empty-fastbreak-cards")
                    .Find(fun x -> x.date = card.date)
                    .ToList()
                |> Seq.collect _.items
                |> Seq.map (fun item -> item.id, item)
                |> dict

            let results = calculateResults scheduleResults cards |> Seq.toArray

            if results.Length > 0 then
                try
                    lockedCardsCollection.BulkWrite(toSelectionStateWriteModels results) |> ignore
                    printf ($"Processed locked card for user {card.userId} and date {card.date}\n")
                with
                | ex -> printf ($"Error processing locked card for user {card.userId} and date {card.date}: {ex.Message}\n")
            else
                printf ($"No cards processed for {card.userId} and date {card.date}\n")
        ()
    }
