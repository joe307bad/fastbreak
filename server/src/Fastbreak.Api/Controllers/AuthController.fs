module api.Controllers.AuthController

open System
open Giraffe
open MongoDB.Driver
open Saturn.Endpoint
open Fastbreak.Shared.Entities
open Fastbreak.Shared.Utils.DeserializeBody
open Fastbreak.Shared.Utils.GenerateRandomUsername
open Fastbreak.Shared.Utils.GoogleAuthPipeline
open Fastbreak.Shared.Utils.TryGetSubject
open Fastbreak.Shared.Utils.JwtUtils
open Fastbreak.Shared.Utils.RefreshTokenUtils
open api.Controllers.ProfileController

type LoginRequest = { googleIdToken: string }

type RefreshRequest = { refreshToken: string }

type AuthResponse = 
    { success: bool
      accessToken: string option
      refreshToken: string option
      userId: string option
      message: string }

let loginHandler (database: IMongoDatabase) : HttpHandler =
    fun next ctx ->
        task {
            let! loginRequest = deserializeBody<LoginRequest> ctx
            
            let! payloadOpt = validateGoogleToken loginRequest.googleIdToken
            
            return!
                match payloadOpt with
                | Some payload ->
                    let googleSubject = payload.Subject
                    let email = payload.Email
                    
                    let profilesCollection: IMongoCollection<Profile> =
                        database.GetCollection<Profile>("profiles")
                    
                    let userFilter = Builders<Profile>.Filter.Eq((_.googleId), googleSubject)
                    let existingUsers = profilesCollection.Find(userFilter).ToList()
                    
                    let userId, userName =
                        if existingUsers.Count = 0 then
                            let newUserId = Guid.NewGuid().ToString()
                            let newUserName = generateRandomUsername()
                            
                            let newProfile = {
                                userId = newUserId
                                googleId = googleSubject
                                userName = newUserName
                                email = Some email
                                updatedAt = DateTime.UtcNow
                            }
                            
                            profilesCollection.InsertOne(newProfile)
                            newUserId, newUserName
                        else
                            existingUsers[0].userId, existingUsers[0].userName
                    
                    let accessToken = generateJwtToken userId email
                    let refreshToken = createRefreshToken database userId
                    
                    let response = {
                        success = true
                        accessToken = Some accessToken
                        refreshToken = Some refreshToken
                        userId = Some userId
                        message = "Login successful"
                    }
                    
                    Successful.ok (json response) next ctx
                    
                | None ->
                    let response = {
                        success = false
                        accessToken = None
                        refreshToken = None
                        userId = None
                        message = "Invalid Google ID token"
                    }
                    
                    RequestErrors.BAD_REQUEST (json response) next ctx
        }

let refreshHandler (database: IMongoDatabase) : HttpHandler =
    fun next ctx ->
        task {
            let! refreshRequest = deserializeBody<RefreshRequest> ctx
            let tokenValidation = validateRefreshToken database refreshRequest.refreshToken
            
            return!
                match tokenValidation with
                | Some userId ->
                    let profilesCollection: IMongoCollection<Profile> =
                        database.GetCollection<Profile>("profiles")
                    
                    let userFilter = Builders<Profile>.Filter.Eq((_.userId), userId)
                    let userProfileResult = profilesCollection.Find(userFilter).FirstOrDefault()
                    let userProfileOpt = Option.ofObj userProfileResult
                    
                    match userProfileOpt with
                    | Some profile ->
                        let email = profile.email |> Option.defaultValue (profile.googleId + "@gmail.com")
                        let accessToken = generateJwtToken userId email
                        let newRefreshToken = createRefreshToken database userId
                        
                        revokeRefreshToken database refreshRequest.refreshToken
                        
                        let response = {
                            success = true
                            accessToken = Some accessToken
                            refreshToken = Some newRefreshToken
                            userId = Some userId
                            message = "Token refreshed successfully"
                        }
                        
                        Successful.ok (json response) next ctx
                        
                    | None ->
                        let response = {
                            success = false
                            accessToken = None
                            refreshToken = None
                            userId = None
                            message = "User not found"
                        }
                        
                        RequestErrors.NOT_FOUND (json response) next ctx
                        
                | None ->
                    let response = {
                        success = false
                        accessToken = None
                        refreshToken = None
                        userId = None
                        message = "Invalid or expired refresh token"
                    }
                    
                    RequestErrors.BAD_REQUEST (json response) next ctx
        }

let authRouter database =
    router {
        post "/auth/login" (loginHandler database)
        post "/auth/refresh" (refreshHandler database)
    }