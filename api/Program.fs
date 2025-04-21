open System
open System.Linq.Expressions
open System.Text.Json
open System.Text.Json.Serialization
open Hangfire.Mongo.Migration.Strategies
open MongoDB.Bson
open MongoDB.Bson.Serialization
open MongoDB.Bson.Serialization.Serializers
open MongoDB.Driver
open Saturn
open api.Controllers.DailyFastbreakController
open Microsoft.AspNetCore.Builder
open Microsoft.Extensions.DependencyInjection
open Hangfire
open Hangfire.Mongo
open DotNetEnv
open Saturn.Endpoint
open api.DailyJob.SchedulePuller
open api.Controllers.LockCardController
open api.DailyJob.CalculateFastbreakCardResults
open api.DailyJob.CalculateStatSheets
open api.Entities
open api.Entities.FastbreakSelections

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

type OptionSerializer<'T>() =
    inherit SerializerBase<'T option>()
    
    override _.Serialize(context, _, value) =
        match value with
        | Some v -> 
            BsonSerializer.Serialize(context.Writer, typeof<'T>, v)
        | None -> 
            context.Writer.WriteNull()
    
    override _.Deserialize(context, _) =
        if context.Reader.CurrentBsonType = BsonType.Null then
            context.Reader.ReadNull()
            None
        else
            Some(BsonSerializer.Deserialize<'T>(context.Reader))

BsonSerializer.RegisterSerializer(typeof<FastbreakSelectionsResult option>, OptionSerializer<FastbreakSelectionsResult>())

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

    let formatted = easternTime.ToString("yyyyMMdd")
    let dateTime = easternTime
    
    (formatted, dateTime)

type JobRunner =
    static member DailyJob() =
        if enableDailyJob then
            let (yesterday, _) = getEasternTime (-1)
            let (today, _) = getEasternTime (0)
            let (tomorrow, _) = getEasternTime (1)
            
            // run at 4 am ET every day - this will hopefully get results for any games
            // that started during primetime on the West coast.
            if enableSchedulePuller then
                try 
                    pullSchedules (database, yesterday, today, tomorrow) |> ignore
                    let (_, now) = getEasternTime (0);
                    printf $"Schedule puller completed at %A{now}\n"
                with ex ->
                    let (_, now) = getEasternTime (0);
                    printf $"Schedule puller failed at %A{now} with error {now}\n"
            else
                let (_, now) = getEasternTime (0);
                printf $"Disabled | Schedule Puller | %A{now}\n"
            
            try 
                let (_, now) = getEasternTime (0);
                calculateFastbreakCardResults (database, yesterday, today, tomorrow) |> ignore
                printf $"Fastbreak card results completed at %A{now}\n"
            with ex ->
                let (_, now) = getEasternTime (0);
                printf $"Fastbreak card results failed at %A{now} with error {ex.Message}\n"
            
            try 
                let (_, now) = getEasternTime (0);
                calculateStatSheets (database, yesterday, today, tomorrow) |> ignore
                printf $"Fastbreak card results completed at %A{now}\n"
            with ex ->
                let (_, now) = getEasternTime (0);
                printf $"Fastbreak card results failed at %A{now} with error {ex.Message}\n"
            
            let (_, now) = getEasternTime (0);
            printf $"Daily job completed at %A{now}\n"
        else
            let (_, now) = getEasternTime (0);
            printf $"Disabled | Daily job | %A{now}\n"

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
