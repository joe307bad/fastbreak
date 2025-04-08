module api.DailyJob.CalculateFastbreakCardResults
open System
open MongoDB.Driver
open Shared

let getEasternTime() =
    let utcNow = DateTime.UtcNow
    let easternZone = TimeZoneInfo.FindSystemTimeZoneById("Eastern Standard Time")
    let easternTime = TimeZoneInfo.ConvertTimeFromUtc(utcNow, easternZone)
    
    // Format as needed
    let formatted = easternTime.ToString("yyyy-MM-dd HH:mm:ss")
    let dateOnly = easternTime.ToString("yyyyMMdd")
    
    (easternTime, formatted, dateOnly)

let calculateFastbreakCardResults (database: IMongoDatabase) =
    task {
        let collection = database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
        let (_, __, etDateOnly) = getEasternTime()

        let filter = Builders<FastbreakSelectionState>.Filter.Eq((fun x -> x.date), etDateOnly)

        let cardsResult = collection.Find(filter).ToEnumerable()
        
        let cards = Seq.cast<FastbreakSelectionState> cardsResult
            
        printf($"Locked cards found for {etDateOnly}: {cards |> Seq.length}\n")
            
        for card in cards do
            printf($"Locked card ID: {card.cardId}")
            ()
        ""
    }