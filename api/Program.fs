open System
open System.Linq.Expressions
open Hangfire.Mongo.Migration.Strategies
open MongoDB.Driver
open Saturn
open Schedule
open Google.Apis.Auth
open Microsoft.AspNetCore.Http
open System.Threading.Tasks
open Giraffe
open Microsoft.AspNetCore.Builder
open Microsoft.Extensions.DependencyInjection
open Hangfire
open Hangfire.Mongo
open DotNetEnv
open Saturn.Endpoint
open api.Todos 

Env.Load()

let mongoUser = Environment.GetEnvironmentVariable "MONGO_USER"
let mongoPass = Environment.GetEnvironmentVariable "MONGO_PASS"
let mongoIp = Environment.GetEnvironmentVariable "MONGO_IP"
let mongoDb = Environment.GetEnvironmentVariable "MONGO_DB"

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



let api = pipeline {
    set_header "x-pipeline-type" "API"
}

let googleAuthPipeline =
    pipeline {
        plug requireGoogleAuth
        set_header "x-pipeline-type" "API"
    }



// TOOD use an env var for mongo password, username, ip address, and db
let mongoConnectionString =
    $"mongodb://{mongoUser}:{mongoPass}@{mongoIp}:27017/{mongoDb}?directConnection=true&authSource=admin"

let replicaSetName = "rs0"

let configureMongoClientSettings () =
    let clientSettings = MongoClientSettings.FromConnectionString(mongoConnectionString)

    // Set the replica set name and server selection timeout directly
    clientSettings.ServerSelectionTimeout <- TimeSpan.FromSeconds(5.0)
    clientSettings.ReplicaSetName <- replicaSetName

    (new MongoClient(clientSettings), clientSettings)

let (mongoClient, mongoSettings) = configureMongoClientSettings ()

let mongoStorage =
    MongoStorage(
        mongoSettings,
        "fastbreak",
        MongoStorageOptions(
            CheckConnection = false,
            MigrationOptions = MongoMigrationOptions(MigrationStrategy = DropMongoMigrationStrategy())
        )
    )
    
let database = mongoClient.GetDatabase("fastbreak")

let configureHangfire (services: IServiceCollection) =
    services.AddHangfire(fun config -> config.UseStorage(mongoStorage) |> ignore)
    |> ignore

    services.AddHangfireServer() |> ignore
    services

type JobRunner =
    static member LogJob() =
        // TODO implement disabling the daily job based on an env var
        // pullTomorrowsSchedule(database) |> ignore;
        printfn "Job executed at %A" DateTime.UtcNow

let scheduleJobs () =
    let methodCall: Expression<Action<JobRunner>> =
        Expression.Lambda<Action<JobRunner>>(
            Expression.Call(typeof<JobRunner>.GetMethod("LogJob")),
            Expression.Parameter(typeof<JobRunner>, "x")
        )

    RecurringJob.AddOrUpdate("every-second-job-3", methodCall, "*/1 * * * *")

// let apiRouter = router {
//     pipe_through api
//     forward "/schedule" scheduleController
// }



let configureApp (app: IApplicationBuilder) =
    app.UseHangfireDashboard() |> ignore
    scheduleJobs ()
    app

let endpointPipe = pipeline {
    plug fetchSession
    plug head
    plug requestId
}

let scheduleController = router {
    get "/" (text "Hey")
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
        use_endpoint_router defaultRouter
        service_config configureHangfire
        use_developer_exceptions
        app_config configureApp
    }

run app
