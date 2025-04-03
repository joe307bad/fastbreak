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
open Utils

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
                ctx.WriteJsonAsync("Not foiuhiuhund") |> ignore
                return Some(ctx)
        }

let googleAuthPipeline =
    pipeline {
        plug requireGoogleAuth
        set_header "x-pipeline-type" "API"
    }

[<CLIMutable>]
type FastbreakSelection =
    { id: string
      userAnswer: string
      points: int
      description: string
      ``type``: string }

[<CLIMutable>]
type FastbreakSelectionState =
    { selections: FastbreakSelection list
      totalPoints: int
      id: string option
      locked: bool option }

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

let lockCardHandler (database: IMongoDatabase) : HttpHandler =
    fun next ctx ->
        task {
            let! maybeState = deserializeBody<FastbreakSelectionState> ctx
            match maybeState with
            | state ->
                return! Successful.ok (json state) next ctx
        }

let lockCardRouter database =
    router {
        pipe_through googleAuthPipeline
        post "/lock" (lockCardHandler database)
    }