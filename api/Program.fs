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
    | null -> false
    | _ -> false
let enableSchedulePuller =
    match Environment.GetEnvironmentVariable "ENABLE_SCHEDULE_PULLER" with
    | "1" -> true
    | "0" -> false
    | null -> false
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

let getEasternTime (addDays) =
    let utcNow = DateTime.UtcNow.AddDays(addDays)
    let easternZone = TimeZoneInfo.FindSystemTimeZoneById("Eastern Standard Time")
    let easternTime = TimeZoneInfo.ConvertTimeFromUtc(utcNow, easternZone)

    easternTime.ToString("yyyyMMdd")

type JobRunner =
    static member DailyJob() =
        if enableDailyJob then
            let yesterday = getEasternTime (-1)
            let today = getEasternTime (0)
            let tomorrow = getEasternTime (1)
            
            // run at 4 am ET everyday - this will hopefully get results for any games
            // that started during primetime on the West coast. The process should be
            // 1. Get the schedules for yesterday, today and tomorrow
            // 2. If any of the schedules contain result info, update the schedules collection
            // 3. Calculate the results for each locked fastbreak card
            // 4. Calculate results for yesterday's fastbreak card
            if enableSchedulePuller then
                pullSchedules (database, yesterday, today, tomorrow)
                printf $"Schedule puller completed at %A{DateTime.UtcNow}"
            else
                printf $"Disabled | Schedule Puller | %A{DateTime.UtcNow}"
            
            // calculate results of yesterday's fastbreak card for each user
            calculateFastbreakCardResults (database, yesterday, today, tomorrow)
            
            // calculate stat sheets for tomorrow for each user
            // 
            printf $"Daily job completed at %A{DateTime.UtcNow}"
        else
            printf $"Disabled | Daily job | %A{DateTime.UtcNow}"

        printf $"Daily job executed at %A{DateTime.UtcNow}"

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
