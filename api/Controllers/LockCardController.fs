module api.Controllers.LockCardController

open System
open System.Threading.Tasks
open Giraffe
open MongoDB.Driver
open MongoDB.Bson.Serialization.Attributes
open Saturn.Endpoint
open api.Entities.FastbreakSelections
open api.Utils.profile
open api.Utils.deserializeBody
open api.Utils.tryGetSubject
open api.Utils.googleAuthPipeline

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
            let googleId = tryGetSubject ctx
            
            let! userId = 
                match googleId with
                | Some gId -> getUserIdFromProfile database gId
                | None -> Task.FromResult(None)

            return!
                match userId with
                | Some userId ->
                    
                    let collection: IMongoCollection<FastbreakSelectionState> =
                            database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

                    let filter =
                        Builders<FastbreakSelectionState>.Filter.And(
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

                    let result =
                        collection.UpdateOne(filter, update, updateOptions)
                    let response = { id = result.UpsertedId.ToString() }
                    Successful.ok (json response) next ctx
                | None -> Successful.ok (json {| error = "Error locking card" |}) next ctx
        }

let getLockCardHandler (database: IMongoDatabase) userId : HttpHandler =
    fun next ctx ->
        task {
            let collection = database.GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")

            let today = DateTime.UtcNow.ToString("yyyyMMdd")
            
            let filter =
                   Builders<FastbreakSelectionState>.Filter.And(
                       Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId),
                       Builders<FastbreakSelectionState>.Filter.Eq(_.date, today)
                   )
            
            let! result = collection.Find(filter).FirstOrDefaultAsync()

            return! json result next ctx
        }

let lockCardRouter database =
    router {
        pipe_through googleAuthPipeline
        post "/lock" (lockCardHandler database)
        getf "/lock/%s" (fun userId -> getLockCardHandler database userId)
    }
