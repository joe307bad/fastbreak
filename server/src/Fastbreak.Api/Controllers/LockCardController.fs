module api.Controllers.LockCardController

open System
open System.Threading.Tasks
open Giraffe
open MongoDB.Driver
open MongoDB.Bson.Serialization.Attributes
open Saturn.Endpoint
open Fastbreak.Shared.Entities
open Fastbreak.Shared.Utils.Profile
open Fastbreak.Shared.Utils.DeserializeBody
open Fastbreak.Shared.Utils.TryGetSubject
open Fastbreak.Shared.Utils.TryGetAuthenticatedUser
open Fastbreak.Shared.Utils.TokenAuthPipeline

type GoogleUser = { subject: string }

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
            
            // Try JWT authentication first
            let authenticatedUser = tryGetAuthenticatedUser ctx
            
            let! userId =
                match authenticatedUser with
                | Some user -> 
                    // JWT authentication successful
                    printfn "Using JWT authentication for userId: %s" user.UserId
                    Task.FromResult(Some user.UserId)
                | None -> 
                    // Fallback to Google authentication
                    printfn "JWT auth failed, trying Google auth fallback"
                    let googleId = tryGetSubject ctx
                    match googleId with
                    | Some gId -> 
                        printfn "Using Google authentication for googleId: %s" gId
                        getUserIdFromProfile database gId
                    | None -> 
                        printfn "Both JWT and Google authentication failed"
                        Task.FromResult(None)

            return!
                match userId with
                | Some userId ->

                    let collection: IMongoCollection<FastbreakSelectionState> =
                        database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

                    let filter =
                        Builders<FastbreakSelectionState>.Filter
                            .And(
                                Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId),
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
                | None -> 
                    let response = { id = "" } // Provide empty id to match Kotlin expectations
                    RequestErrors.BAD_REQUEST (json response) next ctx
        }


let lockCardRouter database =
    router {
        
        pipe_through tokenAuthPipeline
        post "/lock" (lockCardHandler database)
    }
