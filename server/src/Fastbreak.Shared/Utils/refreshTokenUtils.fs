namespace Fastbreak.Shared.Utils

open System
open System.Security.Cryptography
open MongoDB.Driver
open Fastbreak.Shared.Entities
module RefreshTokenUtils =


    let private generateSecureToken () =
        use rng = RandomNumberGenerator.Create()
        let tokenBytes = Array.zeroCreate 32
        rng.GetBytes(tokenBytes)
        Convert.ToBase64String(tokenBytes)

    let private hashToken (token: string) =
        use sha256 = SHA256.Create()
        let tokenBytes = System.Text.Encoding.UTF8.GetBytes(token)
        let hashBytes = sha256.ComputeHash(tokenBytes)
        Convert.ToBase64String(hashBytes)

    let private getRefreshTokenExpiry () =
        Environment.GetEnvironmentVariable("REFRESH_TOKEN_EXPIRY_DAYS")
        |> Option.ofObj
        |> Option.bind (fun x -> 
            match Int32.TryParse(x) with
            | true, value -> Some value
            | false, _ -> None)
        |> Option.defaultValue 30

    let createRefreshToken (database: IMongoDatabase) (userId: string) =
        let token = generateSecureToken()
        let tokenHash = hashToken token
        let expiryDays = getRefreshTokenExpiry()
        
        let refreshToken = {
            tokenId = Guid.NewGuid().ToString()
            userId = userId
            tokenHash = tokenHash
            expiresAt = DateTime.UtcNow.AddDays(float expiryDays)
            createdAt = DateTime.UtcNow
        }
        
        let collection: IMongoCollection<RefreshToken> = 
            database.GetCollection<RefreshToken>("refresh-tokens")
        
        collection.InsertOne(refreshToken)
        token

    let validateRefreshToken (database: IMongoDatabase) (token: string) =
        let tokenHash = hashToken token
        let collection: IMongoCollection<RefreshToken> = 
            database.GetCollection<RefreshToken>("refresh-tokens")
        
        let filter = 
            Builders<RefreshToken>.Filter.And([
                Builders<RefreshToken>.Filter.Eq((_.tokenHash), tokenHash)
                Builders<RefreshToken>.Filter.Gt((_.expiresAt), DateTime.UtcNow)
            ])
        
        let refreshToken = collection.Find(filter).FirstOrDefault()
        
        if isNull (box refreshToken) then
            None
        else
            Some refreshToken.userId

    let revokeRefreshToken (database: IMongoDatabase) (token: string) =
        let tokenHash = hashToken token
        let collection: IMongoCollection<RefreshToken> = 
            database.GetCollection<RefreshToken>("refresh-tokens")
        
        let filter = Builders<RefreshToken>.Filter.Eq((_.tokenHash), tokenHash)
        collection.DeleteOne(filter) |> ignore

    let revokeAllRefreshTokensForUser (database: IMongoDatabase) (userId: string) =
        let collection: IMongoCollection<RefreshToken> = 
            database.GetCollection<RefreshToken>("refresh-tokens")
        
        let filter = Builders<RefreshToken>.Filter.Eq((_.userId), userId)
        collection.DeleteMany(filter) |> ignore