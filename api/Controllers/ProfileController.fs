module api.Controllers.ProfileController

open System
open Giraffe
open MongoDB.Driver
open Saturn.Endpoint
open api.Utils.tryGetSubject
open api.Utils.deserializeBody
open api.Utils.googleAuthPipeline

type Profile = { userName: string; userId: string }

type UserName = { userId: string; userName: string; updatedAt: DateTime }

type SaveResponse = { success: bool; message: string }

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
                        let response = { success = true; message = "Username saved successfully" }
                        Successful.ok (json response) next ctx
                    else
                        let response = { success = false; message = "User ID mismatch" }
                        RequestErrors.BAD_REQUEST (json response) next ctx
                | None ->
                    let response = { success = false; message = "Authentication required" }
                    RequestErrors.FORBIDDEN (json response) next ctx
                    
        }

let profileRouter database =
    router {
        pipe_through googleAuthPipeline
        post "/profile" (saveUserNameHandler database)
    }