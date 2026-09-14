module api.Controllers.ProfileController

open System
open Giraffe
open MongoDB.Bson.Serialization.Attributes
open MongoDB.Driver
open Saturn.Endpoint
open Fastbreak.Shared.Entities
open Fastbreak.Shared.Utils.DeserializeBody
open Fastbreak.Shared.Utils.GenerateRandomUsername

[<BsonIgnoreExtraElements>]
[<CLIMutable>]
type Profile =
    { userId: string
      userName: string
      updatedAt: DateTime }

[<CLIMutable>]
type InitializeProfileRequest = { userId: string }

type SaveResponse = { success: bool; message: string }

type ProfileResponse =
    { userName: string
      userId: string
      lockedFastBreakCard: FastbreakSelectionState option }

let saveProfileHandler (database: IMongoDatabase) : HttpHandler =
    fun next ctx ->
        task {
            let! profile = deserializeBody<Profile> ctx

            return!
                if String.IsNullOrWhiteSpace profile.userId then
                    let response =
                        { success = false
                          message = "userId is required" }

                    RequestErrors.BAD_REQUEST (json response) next ctx
                else
                    let collection: IMongoCollection<Profile> =
                        database.GetCollection<Profile>("profiles")

                    let filter = Builders<Profile>.Filter.Eq((_.userId), profile.userId)

                    let update =
                        Builders<Profile>.Update
                            .Set((_.userName), profile.userName)
                            .Set((_.updatedAt), DateTime.Now)
                            .SetOnInsert((_.userId), profile.userId)

                    let updateOptions = UpdateOptions(IsUpsert = true)

                    collection.UpdateOne(filter, update, updateOptions) |> ignore

                    let response =
                        { success = true
                          message = "Profile saved successfully" }

                    Successful.ok (json response) next ctx
        }

let initializeProfileHandler (database: IMongoDatabase) : HttpHandler =
    fun next ctx ->
        task {
            // Body is optional: `{ "userId": "..." }` returns the existing profile, no body creates a new one
            let! rawBody = getRawBody ctx

            let requestedUserId =
                if String.IsNullOrWhiteSpace rawBody then
                    None
                else
                    let request =
                        System.Text.Json.JsonSerializer.Deserialize<InitializeProfileRequest>(
                            rawBody,
                            System.Text.Json.JsonSerializerOptions(PropertyNameCaseInsensitive = true)
                        )

                    if isNull (box request) || String.IsNullOrWhiteSpace request.userId then
                        None
                    else
                        Some request.userId

            let profilesCollection: IMongoCollection<Profile> =
                database.GetCollection<Profile>("profiles")

            let existingProfile =
                requestedUserId
                |> Option.bind (fun userId ->
                    profilesCollection.Find(Builders<Profile>.Filter.Eq((_.userId), userId)).FirstOrDefault()
                    |> Option.ofObj)

            let response =
                match existingProfile with
                | Some profile ->
                    let lockedFastBreakCard =
                        database
                            .GetCollection<FastbreakSelectionState>("locked-fastbreak-cards")
                            .Find(Builders<FastbreakSelectionState>.Filter.Eq(_.userId, profile.userId))
                            .Sort(Builders<FastbreakSelectionState>.Sort.Descending("createdAt"))
                            .FirstOrDefaultAsync()
                        |> Async.AwaitTask
                        |> Async.RunSynchronously
                        |> Option.ofObj

                    { userId = profile.userId
                      userName = profile.userName
                      lockedFastBreakCard = lockedFastBreakCard }
                | None ->
                    let newProfile =
                        { userId = Guid.NewGuid().ToString()
                          userName = generateRandomUsername ()
                          updatedAt = DateTime.Now }

                    profilesCollection.InsertOne(newProfile)

                    { userId = newProfile.userId
                      userName = newProfile.userName
                      lockedFastBreakCard = None }

            return! Successful.ok (json response) next ctx
        }

let profileRouter database =
    router {
        post "/profile" (saveProfileHandler database)
        post "/profile/initialize" (initializeProfileHandler database)
    }
