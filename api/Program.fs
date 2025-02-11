open System
open System.Linq.Expressions
open System.Text.Json
open System.Text.Json.Serialization
open Hangfire.Mongo.Migration.Strategies
open MongoDB.Bson.Serialization
open MongoDB.Driver
open Saturn
open ScheduleController
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
open SchedulePuller
open api.Todos

Env.Load() |> ignore

let enablePullTomorrowsSchedule =
    match Environment.GetEnvironmentVariable "ENABLE_PULL_TOMORROWS_SCHEDULE" with
    | "1" -> true
    | "0" -> false
    | null -> false // Default value if env var is not set
    | _ -> false

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



let api = pipeline { set_header "x-pipeline-type" "API" }

let googleAuthPipeline =
    pipeline {
        plug requireGoogleAuth
        set_header "x-pipeline-type" "API"
    }

BsonClassMap.RegisterClassMap<ScheduleEntity.Event>(fun cm ->
    cm.AutoMap()
    cm.SetIgnoreExtraElements(true))
|> ignore

let mongoConnectionString =
    $"mongodb://{mongoUser}:{mongoPass}@{mongoIp}:27017/{mongoDb}?directConnection=true&authSource=admin"

let replicaSetName = "rs0"

let configureMongoClientSettings () =
    let clientSettings = MongoClientSettings.FromConnectionString(mongoConnectionString)

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
        if enablePullTomorrowsSchedule then
            pullTomorrowsSchedule (database)
            printfn $"Tomorrows schedule pulled at %A{DateTime.UtcNow}"
        else
            printfn $"Disabled | Pulling tomorrows schedule | %A{DateTime.UtcNow}"

        printfn $"Daily job executed at %A{DateTime.UtcNow}"

let scheduleJobs () =
    let methodCall: Expression<Action<JobRunner>> =
        Expression.Lambda<Action<JobRunner>>(
            Expression.Call(typeof<JobRunner>.GetMethod("LogJob")),
            Expression.Parameter(typeof<JobRunner>, "x")
        )

    RecurringJob.AddOrUpdate("every-second-job-3", methodCall, "*/1 * * * *")

let configureApp (app: IApplicationBuilder) =
    app.UseHangfireDashboard() |> ignore
    scheduleJobs ()
    app

let endpointPipe =
    pipeline {
        plug fetchSession
        plug head
        plug requestId
    }

let defaultRouter =
    router {
        // Public API routes
        forward
            "/api"
            (router {
                pipe_through api
                forward "/schedule" (scheduleController database)
            })

        // Protected routes
        forward
            "/api"
            (router {
                pipe_through googleAuthPipeline
                get "/todos1" Find
            })
    }

let app =
    JsonSerializerOptions(
        PropertyNameCaseInsensitive = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    )
    |> ignore

    application {
        use_endpoint_router defaultRouter
        service_config configureHangfire
        use_developer_exceptions
        app_config configureApp
    }

run app
