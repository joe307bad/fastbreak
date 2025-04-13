module api.Controllers.LockCardController

open System
open System.IO
open System.Text
open System.Text.Json
open System.Threading.Tasks
open Giraffe
open Microsoft.AspNetCore.Http
open MongoDB.Driver
open Saturn
open Saturn.Endpoint
open Google.Apis.Auth
open Shared
open api.Utils.tryGetSubject

let validateGoogleToken (idToken: string) =
    task {
        try
            let! payload = GoogleJsonWebSignature.ValidateAsync(idToken)
            return Some payload
        with ex ->
            return None
    }

let requireGoogleAuth: HttpFunc -> HttpContext -> Task<HttpContext option> =
    fun next ctx ->
        task {
            match ctx.Request.Headers.TryGetValue("Authorization") with
            | true, values when values.Count > 0 ->
                let token = values.[0].Replace("Bearer ", "").Trim()
                let! payloadOpt = validateGoogleToken token

                match payloadOpt with
                | Some payload ->
                    ctx.Items.["GoogleUser"] <- payload
                    return! next ctx
                | None ->
                    ctx.SetStatusCode 401
                    ctx.WriteJsonAsync("Token invalid") |> ignore
                    return Some(ctx)
            | _ ->
                ctx.SetStatusCode 404
                ctx.WriteJsonAsync("Not found") |> ignore
                return Some(ctx)
        }

let googleAuthPipeline =
    pipeline {
        plug requireGoogleAuth
        set_header "x-pipeline-type" "API"
    }



type GoogleUser = { subject: string }

let getRawBody (ctx: HttpContext) =
    task {
        ctx.Request.EnableBuffering() // Allows rereading the stream
        use reader = new StreamReader(ctx.Request.Body, Encoding.UTF8, true, 1024, true)
        let! body = reader.ReadToEndAsync()
        ctx.Request.Body.Position <- 0L // Reset stream position for later reading (e.g., model binding)
        return body
    }

let deserializeBody<'T> (ctx: HttpContext) =
    task {
        let! body = getRawBody ctx
        let options = JsonSerializerOptions(PropertyNameCaseInsensitive = true)
        let result = JsonSerializer.Deserialize<'T>(body, options)
        return result
    }

type LockedCardResponse = { id: string }

let lockCardHandler (database: IMongoDatabase) : HttpHandler =
    fun next ctx ->
        task {
            let! state = deserializeBody<FastbreakSelectionState> ctx
            let userId = tryGetSubject ctx;

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
