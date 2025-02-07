open System
open System.Linq.Expressions
open Hangfire.Mongo.Migration.Strategies
open MongoDB.Driver
open Saturn
open Saturn.Endpoint
open api.Todos
open Google.Apis.Auth
open Microsoft.AspNetCore.Http
open System.Threading.Tasks
open Giraffe
open Microsoft.AspNetCore.Builder
open Microsoft.Extensions.DependencyInjection
open Hangfire
open Hangfire.Mongo

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
    
// TOOD use an env var for mongo password, username, ip address, and db
let mongoConnectionString = "mongodb://USER:PWORD@IP:27017/DB?directConnection=true&authSource=admin"
let replicaSetName = "rs0"

let configureMongoClientSettings () =
    let clientSettings = MongoClientSettings.FromConnectionString(mongoConnectionString)
    
    // Set the replica set name and server selection timeout directly
    clientSettings.ServerSelectionTimeout <- TimeSpan.FromSeconds(5.0)
    clientSettings.ReplicaSetName <- replicaSetName

    clientSettings

let mongoClient = configureMongoClientSettings()
let configureHangfire (services: IServiceCollection) =
    let mongoStorage = MongoStorage(mongoClient, "fastbreak", MongoStorageOptions(
        CheckConnection = false,
        MigrationOptions = MongoMigrationOptions(
            MigrationStrategy = DropMongoMigrationStrategy()
        )
    ))
    services.AddHangfire(fun config -> 
        config.UseStorage(mongoStorage) |> ignore
    ) |> ignore
    services.AddHangfireServer() |> ignore
    services
    
type JobRunner =
    static member LogJob() =
        printfn "Job executed at %A" DateTime.UtcNow

let scheduleJobs () =
    let methodCall: Expression<Action<JobRunner>> = 
        Expression.Lambda<Action<JobRunner>>(
            Expression.Call(typeof<JobRunner>.GetMethod("LogJob")),
            Expression.Parameter(typeof<JobRunner>, "x")  // Required parameter
        )
    
    RecurringJob.AddOrUpdate("every-second-job-3", methodCall, "*/1 * * * *")

let configureApp (app: IApplicationBuilder) =
    app.UseHangfireDashboard() |> ignore
    scheduleJobs ()  // ✅ Run jobs inside app configuration
    app

let app =
    application {
        service_config configureHangfire
        use_developer_exceptions
        use_endpoint_router defaultRouter
        app_config configureApp
    }

run app