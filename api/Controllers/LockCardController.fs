module api.Controllers.LockCardController

open System
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

let lockCardHandler database (next: HttpFunc) (ctx: HttpContext) =
    let schedules = async { return Task.FromResult } |> Async.RunSynchronously
    json schedules next ctx
    


// Protected routes
let lockCardRouter database =
    router {
        pipe_through googleAuthPipeline
        post "/lock" (lockCardHandler database)
    }