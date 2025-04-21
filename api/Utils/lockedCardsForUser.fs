module api.Utils.lockedCardsForUser

open System
open System.Text.Json
open System.Text.RegularExpressions
open MongoDB.Bson
open MongoDB.Bson.IO
open MongoDB.Bson.Serialization
open MongoDB.Driver
open Newtonsoft.Json.Linq
open api.Entities.FastbreakSelections
open api.Utils.asyncMap

/// <summary>
/// Retrieves a locked card for a specific user on a specific date without using custom BSON serializers
/// </summary>
/// <param name="database">MongoDB database instance</param>
/// <param name="id">User ID to search for</param>
/// <param name="yesterday">Date to search for</param>
/// <returns>JSON object representing the locked card document</returns>
let lockedCardForUserAsync (database: IMongoDatabase) (id: string) (yesterday: string) = async {
    // Create a filter to find documents matching userId and date
    let filter =
        Builders<BsonDocument>.Filter.Eq("userId", BsonValue.Create(id))
        |> fun f ->
            Builders<BsonDocument>.Filter
                .And(f, Builders<BsonDocument>.Filter.Eq("date", BsonValue.Create("20250416")))

    // Query the collection for the first matching document
    let! result =
        database
            .GetCollection("locked-fastbreak-cards")
            .Find(filter)
            .FirstOrDefaultAsync()
        |> Async.AwaitTask

    // Convert BsonDocument to actual JSON object with simple integers
    let toJsonObject (document: BsonDocument option) =
        match document with
        | Some doc ->
            let jsonWriterSettings = JsonWriterSettings()
            jsonWriterSettings.OutputMode <- JsonOutputMode.RelaxedExtendedJson
            doc.ToJson(jsonWriterSettings)
        | None -> 
            null  // Empty JSON object if no result

    // Return actual JSON object
    // let jsonObject = doc
    return BsonSerializer.Deserialize(result)
}