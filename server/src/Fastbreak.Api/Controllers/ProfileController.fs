module api.Controllers.ProfileController

open System
open Giraffe
open MongoDB.Bson.Serialization.Attributes
open MongoDB.Driver
open Saturn.Endpoint
open Fastbreak.Shared.Entities
open Fastbreak.Shared.Utils.DeserializeBody
open Fastbreak.Shared.Utils.GenerateRandomUsername
open Fastbreak.Shared.Utils.Profile
open Fastbreak.Shared.Utils.TryGetSubject
open Fastbreak.Shared.Utils.GoogleAuthPipeline

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type Profile =
    { userId: string
      googleId: string
      userName: string
      email: string option
      updatedAt: DateTime }

type SaveResponse = { success: bool; message: string }

type ProfileResponse =
    { userName: string
      userId: string
      lockedFastBreakCard: FastbreakSelectionState option }

let saveProfileHandler (database: IMongoDatabase) : HttpHandler =
    fun next ctx ->
        task {
            let! profile = deserializeBody<Profile> ctx
            let googleId = tryGetSubject ctx

            return!
                match googleId with
                | Some authUserId ->
                    match
                        getUserIdFromProfile database authUserId
                        |> Async.AwaitTask
                        |> Async.RunSynchronously
                    with
                    | Some userId ->
                        if profile.userId = userId then
                            let collection: IMongoCollection<Profile> =
                                database.GetCollection<Profile>("profiles")

                            let filter = Builders<Profile>.Filter.Eq((_.userId), userId)

                            let update =
                                Builders<Profile>.Update
                                    .Set((_.userName), profile.userName)
                                    .Set((_.updatedAt), DateTime.Now)
                                    .Set((_.googleId), authUserId)
                                    .SetOnInsert((_.userId), userId)
                                    .SetOnInsert((_.email), None)

                            let updateOptions = UpdateOptions(IsUpsert = true)

                            let result = collection.UpdateOne(filter, update, updateOptions)

                            let response =
                                { success = true
                                  message = "Profile saved successfully" }

                            Successful.ok (json response) next ctx
                        else
                            let response =
                                { success = false
                                  message = "Profile ID mismatch" }

                            RequestErrors.BAD_REQUEST (json response) next ctx
                    | None ->
                        let response =
                            { success = false
                              message = "Authentication required" }

                        RequestErrors.FORBIDDEN (json response) next ctx
                | None ->
                    let response =
                        { success = false
                          message = "Authentication required" }

                    RequestErrors.FORBIDDEN (json response) next ctx

        }

let initializeProfileHandler (database: IMongoDatabase) : HttpHandler =
    fun next ctx ->
        task {
            let authUserId = tryGetSubject ctx

            return!
                match authUserId with
                | Some userId ->
                    let userNamesCollection: IMongoCollection<Profile> =
                        database.GetCollection<Profile>("profiles")

                    let userFilter = Builders<Profile>.Filter.Eq((_.googleId), userId)
                    let userNameDoc = userNamesCollection.Find(userFilter).ToList()
                    let randomUserName = generateRandomUsername ()
                    let newUserId = Guid.NewGuid().ToString()

                    if userNameDoc.Count = 0 then
                        let newUserName =
                            { userId = newUserId
                              googleId = userId
                              userName = randomUserName
                              email = None
                              updatedAt = DateTime.Now }

                        userNamesCollection.InsertOne(newUserName)

                    let lockedFastBreakCard =
                        if userNameDoc.Count > 0 then
                            database
                                .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                                .Find(
                                    Builders<FastbreakSelectionState>.Filter
                                        .And(
                                            Builders<FastbreakSelectionState>.Filter
                                                .Eq(_.userId, userNameDoc[0].userId)
                                        )
                                )
                                .Sort(Builders<FastbreakSelectionState>.Sort.Descending("createdAt"))
                                .FirstOrDefaultAsync()
                            |> Async.AwaitTask
                            |> Async.RunSynchronously
                            |> Option.ofObj
                        else
                            None

                    let response =
                        { userId =
                            if userNameDoc.Count = 0 then
                                newUserId
                            else
                                userNameDoc[0].userId
                          userName =
                            if userNameDoc.Count = 0 then
                                randomUserName
                            else
                                userNameDoc[0].userName
                          lockedFastBreakCard = lockedFastBreakCard }

                    Successful.ok (json response) next ctx
                | None ->
                    let response =
                        { success = false
                          message = "Authentication required" }

                    RequestErrors.FORBIDDEN (json response) next ctx
        }

let profileRouter database =
    router {
        pipe_through googleAuthPipeline
        post "/profile" (saveProfileHandler database)
        post "/profile/initialize" (initializeProfileHandler database)
    }
