module api.DailyJob.CalculateFastbreakCardResults

open System
open MongoDB.Driver
open Shared
open api.Entities.EmptyFastbreakCard

let calculateFastbreakCardResults (database: IMongoDatabase, yesterday, today, tomorrow) =
    task {
        let date = yesterday;

        let scheduleResults =
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

        for card in cards do
            let selection = card.selections[0]
            let result = scheduleResults.Item(selection.id)
            let points = if selection.userAnswer.Equals(result.correctAnswer) then result.points else 0;
            printf $"---- Card ID: {card.cardId}\n"
            printf $"---- Event ID: {result.id}\n"
            printf $"{result.awayTeam} vs {result.homeTeam}\n"
            printf $"Winner: {result.correctAnswer}\n"
            printf $"User pick: {selection.userAnswer}\n"
            printf $"Points: {points}\n"
            printf $"-------- /\n"
            ()

        ""
    }
