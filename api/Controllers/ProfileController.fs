module api.Controllers.ProfileController

open System
open Giraffe
open MongoDB.Bson.Serialization.Attributes
open MongoDB.Driver
open Saturn.Endpoint
open api.Entities.FastbreakSelections
open api.Utils.generateRandomUsername
open api.Utils.tryGetSubject
open api.Utils.deserializeBody
open api.Utils.googleAuthPipeline

type Profile = { userName: string; userId: string }

[<BsonIgnoreExtraElements>]
type UserName =
    { userId: string
      userName: string
      updatedAt: DateTime }

type SaveResponse = { success: bool; message: string }

type ProfileResponse =
    { userName: string
      lockedFastBreakCard: FastbreakSelectionState option }

let saveUserNameHandler (database: IMongoDatabase) : HttpHandler =
    fun next ctx ->
        task {
            let! profile = deserializeBody<Profile> ctx
            let userId = tryGetSubject ctx

            return!
                match userId with
                | Some authUserId ->
                    if profile.userId = authUserId then
                        let collection: IMongoCollection<UserName> =
                            database.GetCollection<UserName>("userNames")

                        let filter = Builders<UserName>.Filter.Eq((fun x -> x.userId), authUserId)

                        let update =
                            Builders<UserName>.Update
                                .Set((fun x -> x.userName), profile.userName)
                                .Set((fun x -> x.updatedAt), DateTime.Now)
                                .SetOnInsert((fun x -> x.userId), authUserId)

                        let updateOptions = UpdateOptions(IsUpsert = true)

                        let result = collection.UpdateOne(filter, update, updateOptions)

                        let response =
                            { success = true
                              message = "Username saved successfully" }

                        Successful.ok (json response) next ctx
                    else
                        let response =
                            { success = false
                              message = "User ID mismatch" }

                        RequestErrors.BAD_REQUEST (json response) next ctx
                | None ->
                    let response =
                        { success = false
                          message = "Authentication required" }

                    RequestErrors.FORBIDDEN (json response) next ctx

        }

let initializeProfileHandler (database: IMongoDatabase) requestedUserId : HttpHandler =
    fun next ctx ->
        task {
            let authUserId = tryGetSubject ctx

            return!
                match authUserId with
                | Some userId ->
                    if requestedUserId = userId then
                        
                        // Check if username exists for this user
                        let userNamesCollection: IMongoCollection<UserName> =
                            database.GetCollection<UserName>("userNames")
                        
                        let userFilter = Builders<UserName>.Filter.Eq((fun x -> x.userId), userId)
                        let userNameDoc = userNamesCollection.Find(userFilter).ToList()
                        let randomUserName = generateRandomUsername()
                        
                        // If no username exists, create one
                        if userNameDoc.Count = 0 then
                            let newUserName = {
                                userId = userId
                                userName = randomUserName
                                updatedAt = DateTime.Now
                            }
                            userNamesCollection.InsertOne(newUserName)  

                        let lockedFastBreakCard =
                            database
                                .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                                .Find(
                                    Builders<FastbreakSelectionState>.Filter
                                        .And(Builders<FastbreakSelectionState>.Filter.Eq(_.userId, userId))
                                )
                                .Sort(Builders<FastbreakSelectionState>.Sort.Descending("createdAt"))
                                .FirstOrDefaultAsync()
                            |> Async.AwaitTask
                            |> Async.RunSynchronously
                            |> Option.ofObj
                        let response =
                            { userName = if userNameDoc.Count = 0 then randomUserName else userNameDoc[0].userName
                              lockedFastBreakCard = lockedFastBreakCard }

                        Successful.ok (json response) next ctx
                    else
                        let response =
                            { success = false
                              message = "User ID mismatch" }

                        RequestErrors.BAD_REQUEST (json response) next ctx
                | None ->
                    let response =
                        { success = false
                          message = "Authentication required" }

                    RequestErrors.FORBIDDEN (json response) next ctx
        }

let profileRouter database =
    router { 
        pipe_through googleAuthPipeline
        post "/profile" (saveUserNameHandler database)
        postf "/profile/initialize/%s"  (fun userId -> requireGoogleAuth >=> (initializeProfileHandler database userId))
    }
