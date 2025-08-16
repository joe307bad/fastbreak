module api.Utils.tokenAuthPipeline

open System.Threading.Tasks
open Giraffe
open Microsoft.AspNetCore.Http
open Saturn
open api.Utils.jwtUtils

type AuthenticatedUser = {
    UserId: string
    Email: string
}

let requireTokenAuth: HttpFunc -> HttpContext -> Task<HttpContext option> =
    fun next ctx ->
        task {
            printfn "Token auth pipeline called"
            match ctx.Request.Headers.TryGetValue("Authorization") with
            | true, values when values.Count > 0 ->
                let token = values.[0].Replace("Bearer ", "").Trim()
                printfn "Found Authorization header with token: %s" (token.Substring(0, min 20 token.Length) + "...")
                let validationResult = validateJwtToken token

                match validationResult with
                | Some (userId, email) ->
                    printfn "Token validation successful - userId: %s, email: %s" userId email
                    let authenticatedUser = { UserId = userId; Email = email }
                    ctx.Items.["AuthenticatedUser"] <- authenticatedUser
                    printfn "Stored AuthenticatedUser in context: %A" authenticatedUser
                    return! next ctx
                | None ->
                    printfn "Token validation failed"
                    ctx.SetStatusCode 401
                    ctx.WriteJsonAsync("Token invalid") |> ignore
                    return Some(ctx)
            | _ ->
                printfn "No Authorization header found"
                ctx.SetStatusCode 401
                ctx.WriteJsonAsync("Authentication required") |> ignore
                return Some(ctx)
        }

let tokenAuthPipeline =
    pipeline {
        plug requireTokenAuth
        set_header "x-pipeline-type" "API"
    }