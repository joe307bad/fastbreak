open Saturn
open Saturn.Endpoint
open api.Todos
open Google.Apis.Auth
open Microsoft.AspNetCore.Http
open System.Threading.Tasks
open Giraffe

let validateGoogleToken (idToken: string) =
    task {
        try
            let! payload = GoogleJsonWebSignature.ValidateAsync(idToken)
            return Some payload
        with
        | ex ->
            return None
    }
    
let requireGoogleAuth : HttpFunc -> HttpContext -> Task<HttpContext option> =
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



let api =
    pipeline {
        set_header "x-pipeline-type" "API"
    }

let googleAuthPipeline =
    pipeline {
        plug requireGoogleAuth
        set_header "x-pipeline-type" "API"
    }

let defaultRouter =
    router {
        pipe_through api
        get "/api/todos" Find
        getf "/api/todos/%i" FindOne
        post "/api/todos" Create
        putf "/api/todos/%i" Update
        deletef "/api/todos/%i" Delete
        
        pipe_through googleAuthPipeline  
        get "/api/todos" Find
    }

let app =
    application {
        use_developer_exceptions
        use_endpoint_router defaultRouter
    }

run app