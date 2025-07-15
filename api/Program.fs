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
open api.DailyJob.DailyJob
open api.Controllers.LockCardController
open api.Entities
open api.Entities.FastbreakSelections
open api.Entities.StatSheet

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

type OptionJsonConverter() =
    inherit JsonConverter()
    
    override _.CanConvert(objectType) =
        objectType.IsGenericType && objectType.GetGenericTypeDefinition() = typedefof<_ option>
    
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
    
    override _.CanConvert(objectType) =
        objectType.IsArray
    
    override _.WriteJson(writer, value, serializer) =
        match value with
        | null -> 
            writer.WriteStartArray()
            writer.WriteEndArray()
        | _ -> 
            let tempConverter = serializer.Converters |> Seq.find (fun c -> c.GetType() = typeof<SafeArrayJsonConverter>)
            serializer.Converters.Remove(tempConverter) |> ignore
            serializer.Serialize(writer, value)
            serializer.Converters.Add(tempConverter)
    
    override _.ReadJson(reader, objectType, existingValue, serializer) =
        if reader.TokenType = JsonToken.Null then
            System.Array.CreateInstance(objectType.GetElementType(), 0)
        else
            serializer.Deserialize(reader, objectType)

BsonSerializer.RegisterSerializer(typeof<FastbreakSelectionsResult option>, OptionSerializer<FastbreakSelectionsResult>())
BsonSerializer.RegisterSerializer(typeof<int option>, OptionSerializer<int>())
BsonSerializer.RegisterSerializer(typeof<bool option>, OptionSerializer<bool>())
BsonSerializer.RegisterSerializer(typeof<FastbreakCard option>, OptionSerializer<FastbreakCard>())
BsonSerializer.RegisterSerializer(typeof<PerfectFastbreakCards option>, OptionSerializer<PerfectFastbreakCards>())

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
            dailyJob enableSchedulePuller database
        else
            let (_, now) = getEasternTime (0);
            printf $"Daily job did not run at %A{now} because its disabled\n"

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
