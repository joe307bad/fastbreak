module api.Utils.googleAuthPipeline

open System.Threading.Tasks
open Giraffe
open Google.Apis.Auth
open Microsoft.AspNetCore.Http
open Saturn

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
            // Debug: Force 401 for POST /api/lock to test authentication error handling
            // #if DEBUG
            // if ctx.Request.Method = "POST" && ctx.Request.Path.Value = "/api/lock" then
            //     ctx.SetStatusCode 401
            //     ctx.WriteJsonAsync("Debug: Authentication required") |> ignore
            //     return Some(ctx)
            // else
            // #endif
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
