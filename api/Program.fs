open System
open System.Linq.Expressions
open System.Text.Json
open System.Text.Json.Serialization
open Hangfire.Mongo.Migration.Strategies
open MongoDB.Bson.Serialization
open MongoDB.Driver
open Saturn
open DailyFastbreakController
open Microsoft.AspNetCore.Builder
open Microsoft.Extensions.DependencyInjection
open Hangfire
open Hangfire.Mongo
open DotNetEnv
open Saturn.Endpoint
open SchedulePuller
open api.Controllers.LockCardController
open api.DailyJob.CalculateFastbreakCardResults

Env.Load() |> ignore

let enableDailyJob =
    match Environment.GetEnvironmentVariable "ENABLE_DAILY_JOB" with
    | "1" -> true
    | "0" -> false
    | null -> false // Default value if env var is not set
    | _ -> false

let mongoUser = Environment.GetEnvironmentVariable "MONGO_USER"
let mongoPass = Environment.GetEnvironmentVariable "MONGO_PASS"
let mongoIp = Environment.GetEnvironmentVariable "MONGO_IP"
let mongoDb = Environment.GetEnvironmentVariable "MONGO_DB"


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

let database: IMongoDatabase = mongoClient.GetDatabase("fastbreak")

let configureHangfire (services: IServiceCollection) =
    services.AddHangfire(fun config -> config.UseStorage(mongoStorage) |> ignore)
    |> ignore

    services.AddHangfireServer() |> ignore
    services

type JobRunner =
    static member DailyJob() =
        if enableDailyJob then
            // run at 11:00pm each day
            pullTomorrowsSchedule (database)
            
            // calculate results of the past day's fastbreak card for each user
            calculateFastbreakCardResults (database)
            
            // calculate stat sheets for tomorrow for each user
            // 
            printfn $"Daily job completed at %A{DateTime.UtcNow}"
        else
            printfn $"Disabled | Daily job | %A{DateTime.UtcNow}"

        printfn $"Daily job executed at %A{DateTime.UtcNow}"

let scheduleJobs () =
    let methodCall: Expression<Action<JobRunner>> =
        Expression.Lambda<Action<JobRunner>>(
            Expression.Call(typeof<JobRunner>.GetMethod("DailyJob")),
            Expression.Parameter(typeof<JobRunner>, "x")
        )

    RecurringJob.AddOrUpdate("daily-job", methodCall, "*/1 * * * *")

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
    
let apiRouter =
    router {
        forward "" (lockCardRouter database)
        forward "" (dailyFastbreakRouter database)
    }

let appRouter =
    router {
        forward "/api" apiRouter
    }

let app =
    JsonSerializerOptions(
        PropertyNameCaseInsensitive = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    )
    |> ignore

    application {
        url "http://0.0.0.0:8085"
        use_endpoint_router appRouter
        service_config configureHangfire
        use_developer_exceptions
        app_config configureApp
    }

run app
