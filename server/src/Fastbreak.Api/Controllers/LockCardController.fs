module api.Controllers.LockCardController

open System
open Giraffe
open MongoDB.Driver
open MongoDB.Bson.Serialization.Attributes
open Saturn.Endpoint
open Fastbreak.Shared.Entities
open Fastbreak.Shared.Utils.DeserializeBody

type LockedCardResponse = { id: string }

[<BsonIgnoreExtraElements>]
type UserName =
    { userId: string
      userName: string
      updatedAt: DateTime }

let lockCardHandler (database: IMongoDatabase) : HttpHandler =
    fun next ctx ->
        task {
            let! state = deserializeBody<FastbreakSelectionState> ctx

            return!
                if String.IsNullOrWhiteSpace state.userId then
                    let response = { id = "" } // Provide empty id to match Kotlin expectations
                    RequestErrors.BAD_REQUEST (json response) next ctx
                else
                    let collection: IMongoCollection<FastbreakSelectionState> =
                        database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

                    let filter =
                        Builders<FastbreakSelectionState>.Filter
                            .And(
                                Builders<FastbreakSelectionState>.Filter.Eq(_.userId, state.userId),
                                Builders<FastbreakSelectionState>.Filter.Eq(_.date, state.date)
                            )

                    let update =
                        Builders<FastbreakSelectionState>.Update
                            .Set(_.selections, state.selections)
                            .Set(_.totalPoints, state.totalPoints)
                            .Set(_.cardId, state.cardId)
                            .Set(_.createdAt, DateTime.Now)

                    let updateOptions = UpdateOptions(IsUpsert = true)

                    let result = collection.UpdateOne(filter, update, updateOptions)

                    // Generate a response ID - use UpsertedId for new documents, or generate one for updates
                    let responseId =
                        if result.UpsertedId <> null then
                            result.UpsertedId.ToString()
                        else
                            Guid.NewGuid().ToString() // Generate ID for updates

                    let response = { id = responseId }
                    Successful.ok (json response) next ctx
        }


let lockCardRouter database =
    router {
        post "/lock" (lockCardHandler database)
    }
