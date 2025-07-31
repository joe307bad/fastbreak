open System
open System.Linq.Expressions
open Hangfire.Mongo.Migration.Strategies
open MongoDB.Bson
open MongoDB.Bson.Serialization
open MongoDB.Bson.Serialization.Serializers
open MongoDB.Driver
open Saturn
open Newtonsoft.Json
open Newtonsoft.Json.Serialization
open Giraffe
open api.Controllers.DailyFastbreakController
open Microsoft.AspNetCore.Builder
open Microsoft.Extensions.DependencyInjection
open Hangfire
open Hangfire.Mongo
open DotNetEnv
open Saturn.Endpoint
open api.Controllers.ProfileController
open api.DailyJob.DailyJob
open api.Controllers.LockCardController
open api.Entities
open api.Entities.FastbreakSelections
open api.Entities.StatSheet
open api.Entities.Leaderboard

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
        | Some v -> BsonSerializer.Serialize(context.Writer, typeof<'T>, v)
        | None -> context.Writer.WriteNull()

    override _.Deserialize(context, _) =
        if context.Reader.CurrentBsonType = BsonType.Null then
            context.Reader.ReadNull()
            None
        else
            Some(BsonSerializer.Deserialize<'T>(context.Reader))

type OptionJsonConverter() =
    inherit JsonConverter()

    override _.CanConvert(objectType) =
        objectType.IsGenericType
        && objectType.GetGenericTypeDefinition() = typedefof<_ option>

    override _.WriteJson(writer, value, serializer) =
        match value with
        | null -> writer.WriteNull()
        | _ ->
            let optionValue = value.GetType().GetProperty("Value").GetValue(value)

            if isNull optionValue then
                writer.WriteNull()
            else
                serializer.Serialize(writer, optionValue)

    override _.ReadJson(reader, objectType, existingValue, serializer) =
        if reader.TokenType = JsonToken.Null then
            null
        else
            let innerType = objectType.GetGenericArguments().[0]
            let value = serializer.Deserialize(reader, innerType)
            let someMethod = objectType.GetMethod("Some")
            someMethod.Invoke(null, [| value |])

type SafeArrayJsonConverter() =
    inherit JsonConverter()

    override _.CanConvert(objectType) = objectType.IsArray

    override _.WriteJson(writer, value, serializer) =
        match value with
        | null ->
            writer.WriteStartArray()
            writer.WriteEndArray()
        | _ ->
            let tempConverter =
                serializer.Converters
                |> Seq.find (fun c -> c.GetType() = typeof<SafeArrayJsonConverter>)

            serializer.Converters.Remove(tempConverter) |> ignore
            serializer.Serialize(writer, value)
            serializer.Converters.Add(tempConverter)

    override _.ReadJson(reader, objectType, existingValue, serializer) =
        if reader.TokenType = JsonToken.Null then
            System.Array.CreateInstance(objectType.GetElementType(), 0)
        else
            serializer.Deserialize(reader, objectType)

BsonSerializer.RegisterSerializer(
    typeof<FastbreakSelectionsResult option>,
    OptionSerializer<FastbreakSelectionsResult>()
)

BsonSerializer.RegisterSerializer(typeof<int option>, OptionSerializer<int>())
BsonSerializer.RegisterSerializer(typeof<bool option>, OptionSerializer<bool>())
BsonSerializer.RegisterSerializer(typeof<FastbreakCard option>, OptionSerializer<FastbreakCard>())

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
    services.AddHangfire(fun config ->
        config.UseStorage(mongoStorage) |> ignore
        config.UseFilter(new Hangfire.AutomaticRetryAttribute(Attempts = 3)) |> ignore)
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
            dailyJob enableSchedulePuller database
        else
            let (_, now) = getEasternTime (0)
            printf $"Daily job did not run at %A{now} because its disabled\n"

    static member FastbreakCardResultsJob() =
        let (_, startTime) = getEasternTime (0)
        let stopwatch = System.Diagnostics.Stopwatch.StartNew()
        printf $"FastbreakCardResultsJob started at %A{startTime}\n"

        try
            api.DailyJob.CalculateFastbreakCardResults.calculateFastbreakCardResults database
            |> ignore

            stopwatch.Stop()
            let (_, endTime) = getEasternTime (0)

            printf
                $"FastbreakCardResultsJob completed at %A{endTime} (duration: {stopwatch.Elapsed.TotalSeconds:F2}s)\n"
        with ex ->
            stopwatch.Stop()
            let (_, endTime) = getEasternTime (0)

            printf
                $"FastbreakCardResultsJob failed at %A{endTime} (duration: {stopwatch.Elapsed.TotalSeconds:F2}s) with error {ex.Message}\n"

    static member StatSheetsJob() =
        let (_, startTime) = getEasternTime (0)
        let stopwatch = System.Diagnostics.Stopwatch.StartNew()
        printf $"StatSheetsJob started at %A{startTime}\n"

        try
            let (twoDaysAgo, _) = getEasternTime (-2)
            let (yesterday, _) = getEasternTime (-1)
            let (today, _) = getEasternTime (0)
            let (tomorrow, _) = getEasternTime (1)

            api.DailyJob.CalculateStatSheets.calculateStatSheets (database, twoDaysAgo, yesterday, today, tomorrow)
            |> ignore

            stopwatch.Stop()
            let (_, endTime) = getEasternTime (0)
            printf $"StatSheetsJob completed at %A{endTime} (duration: {stopwatch.Elapsed.TotalSeconds:F2}s)\n"
        with ex ->
            stopwatch.Stop()
            let (_, endTime) = getEasternTime (0)

            printf
                $"StatSheetsJob failed at %A{endTime} (duration: {stopwatch.Elapsed.TotalSeconds:F2}s) with error {ex.Message}\n"

    static member LeaderboardJob() =
        let (_, startTime) = getEasternTime (0)
        let stopwatch = System.Diagnostics.Stopwatch.StartNew()
        printf $"LeaderboardJob started at %A{startTime}\n"

        try
            let monday = api.Utils.getWeekDays.getLastMonday ()
            let mondayId = monday.ToString("yyyyMMdd")

            let statSheets =
                database
                    .GetCollection<StatSheet>("user-stat-sheets")
                    .Find(Builders<StatSheet>.Filter.Gte(_.createdAt, monday))
                    .ToList()
                |> Seq.toList

            let leaderboards =
                api.DailyJob.CalculateLeaderboards.calculateLeaderboard database statSheets mondayId
                |> Async.AwaitTask
                |> Async.RunSynchronously

            database
                .GetCollection<api.Entities.Leaderboard.Leaderboard>("leaderboards")
                .ReplaceOne(
                    Builders<api.Entities.Leaderboard.Leaderboard>.Filter.Eq(_.id, mondayId),
                    { id = mondayId; items = leaderboards },
                    ReplaceOptions(IsUpsert = true)
                )
            |> ignore

            stopwatch.Stop()
            let (_, endTime) = getEasternTime (0)

            printf
                $"LeaderboardJob completed at %A{endTime} (duration: {stopwatch.Elapsed.TotalSeconds:F2}s) for week {mondayId}\n"
        with ex ->
            stopwatch.Stop()
            let (_, endTime) = getEasternTime (0)

            printf
                $"LeaderboardJob failed at %A{endTime} (duration: {stopwatch.Elapsed.TotalSeconds:F2}s) with error {ex.Message}\n"

let scheduleJobs () =
    let dailyJobCall: Expression<Action<JobRunner>> =
        Expression.Lambda<Action<JobRunner>>(
            Expression.Call(typeof<JobRunner>.GetMethod("DailyJob")),
            Expression.Parameter(typeof<JobRunner>, "x")
        )

    let fastbreakCardResultsCall: Expression<Action<JobRunner>> =
        Expression.Lambda<Action<JobRunner>>(
            Expression.Call(typeof<JobRunner>.GetMethod("FastbreakCardResultsJob")),
            Expression.Parameter(typeof<JobRunner>, "x")
        )

    let statSheetsCall: Expression<Action<JobRunner>> =
        Expression.Lambda<Action<JobRunner>>(
            Expression.Call(typeof<JobRunner>.GetMethod("StatSheetsJob")),
            Expression.Parameter(typeof<JobRunner>, "x")
        )

    let leaderboardCall: Expression<Action<JobRunner>> =
        Expression.Lambda<Action<JobRunner>>(
            Expression.Call(typeof<JobRunner>.GetMethod("LeaderboardJob")),
            Expression.Parameter(typeof<JobRunner>, "x")
        )

    // Schedule jobs with timezone awareness for ET
    let easternTimeZone = TimeZoneInfo.FindSystemTimeZoneById("Eastern Standard Time")
    let recurringJobOptions = RecurringJobOptions(TimeZone = easternTimeZone)

    RecurringJob.AddOrUpdate("daily-job", dailyJobCall, "30 3 * * *")
    RecurringJob.AddOrUpdate("fastbreak-card-results-job", fastbreakCardResultsCall, "0 4 * * *", recurringJobOptions)
    RecurringJob.AddOrUpdate("stat-sheets-job", statSheetsCall, "0 4 * * *", recurringJobOptions)
    RecurringJob.AddOrUpdate("leaderboard-job", leaderboardCall, "0 4 * * *", recurringJobOptions)

let configureApp (app: IApplicationBuilder) =
    app.UseHangfireDashboard() |> ignore
    scheduleJobs ()

    let immediateJobCall: Expression<Action<JobRunner>> =
        Expression.Lambda<Action<JobRunner>>(
            Expression.Call(typeof<JobRunner>.GetMethod("DailyJob")),
            Expression.Parameter(typeof<JobRunner>, "x")
        )

    BackgroundJob.Enqueue(immediateJobCall) |> ignore
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
        forward "" (profileRouter database)
    }

let healthHandler: HttpHandler =
    fun next ctx ->
        ctx.WriteJsonAsync(
            {| status = "healthy"
               timestamp = DateTime.UtcNow |}
        )

let appRouter =
    router {
        get "/" healthHandler
        forward "/api" apiRouter
    }

let configureJson (services: IServiceCollection) =
    let jsonSettings = JsonSerializerSettings()
    jsonSettings.Converters.Add(OptionJsonConverter())
    jsonSettings.Converters.Add(SafeArrayJsonConverter())
    jsonSettings.NullValueHandling <- NullValueHandling.Include
    jsonSettings.ContractResolver <- CamelCasePropertyNamesContractResolver()
    services.AddSingleton<Json.ISerializer>(NewtonsoftJson.Serializer(jsonSettings))

let app =
    application {
        url "http://0.0.0.0:8085"
        use_endpoint_router appRouter
        service_config (configureHangfire >> configureJson)
        use_developer_exceptions
        app_config configureApp
    }

run app
