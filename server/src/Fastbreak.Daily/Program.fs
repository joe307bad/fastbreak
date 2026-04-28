open System
open System.Text.Json
open System.Text.Json.Serialization
open Argu
open Amazon
open Amazon.S3
open Amazon.S3.Model
open Amazon.DynamoDBv2
open Amazon.DynamoDBv2.Model
open Fastbreak.Daily.Main

type CliArgs =
    | [<CustomCommandLine("v1"); CliPrefix(CliPrefix.None)>] V1
    | V2
    | [<CustomCommandLine("enrich-topics"); CliPrefix(CliPrefix.None)>] EnrichTopics
    | [<CustomCommandLine("enrich-data-points"); CliPrefix(CliPrefix.None)>] EnrichDataPoints
    | [<CustomCommandLine("generate-and-enrich-topics"); CliPrefix(CliPrefix.None)>] GenerateAndEnrichTopics
    | [<AltCommandLine("--output-dir", "-o")>] OutputDir of string
    | [<AltCommandLine("--topics-json")>] TopicsJson of string
    | [<AltCommandLine("--enriched-json")>] EnrichedJson of string
    | Version
    interface IArgParserTemplate with
        member this.Usage =
            match this with
            | V1 -> "run the v1 narrative pipeline (default)"
            | V2 -> "run the v2 topics pipeline"
            | EnrichTopics -> "enrich an existing topics JSON file with segments, teams, and players"
            | EnrichDataPoints -> "enrich an existing topics-enriched JSON file with chart-derived data points"
            | GenerateAndEnrichTopics -> "run full v2 pipeline: generate, enrich with segments, enrich with data points, upload to S3 / update DynamoDB"
            | OutputDir _ -> "directory to write generated topics into (v2 only)"
            | TopicsJson _ -> "path to topics JSON file (required for enrich-topics)"
            | EnrichedJson _ -> "path to topics-enriched JSON file (required for enrich-data-points)"
            | Version -> "display version information"

let getRegion () =
    let regionStr = Environment.GetEnvironmentVariable("AWS_REGION")
                    |> Option.ofObj
                    |> Option.defaultValue "us-east-1"
    RegionEndpoint.GetBySystemName(regionStr)

let uploadToS3 (bucket: string) (key: string) (json: string) = async {
    use client = new AmazonS3Client(getRegion())
    let request = PutObjectRequest(
        BucketName = bucket,
        Key = key,
        ContentBody = json,
        ContentType = "application/json"
    )
    let! _ = client.PutObjectAsync(request) |> Async.AwaitTask
    printfn "Uploaded to S3: s3://%s/%s" bucket key
}

let updateDynamoDB (tableName: string) (fileKey: string) (title: string) = async {
    use client = new AmazonDynamoDBClient(getRegion())
    let utcTimestamp = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
    let item = Collections.Generic.Dictionary<string, AttributeValue>()
    item.["file_key"] <- AttributeValue(S = fileKey)
    item.["updatedAt"] <- AttributeValue(S = utcTimestamp)
    item.["title"] <- AttributeValue(S = title)
    item.["interval"] <- AttributeValue(S = "daily")
    item.["type"] <- AttributeValue(S = "topics")

    let request = PutItemRequest(
        TableName = tableName,
        Item = item
    )
    let! _ = client.PutItemAsync(request) |> Async.AwaitTask
    printfn "Updated DynamoDB: %s key: %s updatedAt: %s title: %s" tableName fileKey utcTimestamp title
}

let runV1 () =
    let narratives = run() |> Async.RunSynchronously

    let descriptionSegments = [|
        {| ``type`` = "text"; value = "Daily sports aggregation powered by Google Gemini with "; url = Option<string>.None |}
        {| ``type`` = "link"; value = "grounded search"; url = Some "https://cloud.google.com/vertex-ai/generative-ai/docs/grounding/grounding-with-google-search" |}
        {| ``type`` = "text"; value = " and Vertex AI to achieve accuracy and up-to-date information. Statistical analysis uses Fastbreak charts built with open source packages including "; url = Option<string>.None |}
        {| ``type`` = "link"; value = "nflfastR"; url = Some "https://www.nflfastr.com/" |}
        {| ``type`` = "text"; value = " (NFL play-by-play), "; url = Option<string>.None |}
        {| ``type`` = "link"; value = "hoopR"; url = Some "https://hoopr.sportsdataverse.org/" |}
        {| ``type`` = "text"; value = " (NBA stats), and the "; url = Option<string>.None |}
        {| ``type`` = "link"; value = "NHL Stats API"; url = Some "https://github.com/Zmalski/NHL-API-Reference" |}
        {| ``type`` = "text"; value = "."; url = Option<string>.None |}
    |]

    let output = {|
        date = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
        descriptionSegments = descriptionSegments
        narratives = narratives
    |}

    let options = JsonSerializerOptions(WriteIndented = true)
    let json = JsonSerializer.Serialize(output, options)
    (json, "topics.json", "Daily Sports Narratives", List.length narratives)

let runV2 (outputDir: string) =
    let topics = Fastbreak.Daily.V2.Main.run outputDir |> Async.RunSynchronously
    List.length topics

// Master service flow: chain the three v2 stages in-memory and produce the upload envelope.
// No filesystem output — intermediate results live only as F# values until the final upload.
let runGenerateAndEnrich () = async {
    let! generated = Fastbreak.Daily.V2.Main.runService ()
    let! enriched = Fastbreak.Daily.V2.TopicEnricher.enrichTopicsCore generated
    let! withData = Fastbreak.Daily.V2.DataPointEnricher.enrichWithDataPointsCore enriched

    let infoSegments = [|
        {| ``type`` = "text"; value = "Daily sports topics powered by Google Gemini with "; url = Option<string>.None |}
        {| ``type`` = "link"; value = "grounded Google Search"; url = Some "https://cloud.google.com/vertex-ai/generative-ai/docs/grounding/grounding-with-google-search" |}
        {| ``type`` = "text"; value = " on Vertex AI for accurate, up-to-date narratives. Each topic is enriched with supporting data points pulled from Fastbreak charts, which are built daily from open-source sports data including "; url = Option<string>.None |}
        {| ``type`` = "link"; value = "nflfastR"; url = Some "https://www.nflfastr.com/" |}
        {| ``type`` = "text"; value = " (NFL play-by-play), "; url = Option<string>.None |}
        {| ``type`` = "link"; value = "hoopR"; url = Some "https://hoopr.sportsdataverse.org/" |}
        {| ``type`` = "text"; value = " (NBA stats), "; url = Option<string>.None |}
        {| ``type`` = "link"; value = "baseballr"; url = Some "https://billpetti.github.io/baseballr/" |}
        {| ``type`` = "text"; value = " (MLB stats), and the "; url = Option<string>.None |}
        {| ``type`` = "link"; value = "NHL Stats API"; url = Some "https://github.com/Zmalski/NHL-API-Reference" |}
        {| ``type`` = "text"; value = ". Tap any underlined stat to jump to the matching chart."; url = Option<string>.None |}
    |]

    let info = "Daily sports topics powered by Google Gemini with grounded Google Search on Vertex AI for accurate, up-to-date narratives. Each topic is enriched with supporting data points pulled from Fastbreak charts, which are built daily from open-source sports data including nflfastR (NFL play-by-play), hoopR (NBA stats), baseballr (MLB stats), and the NHL Stats API. Tap any underlined stat to jump to the matching chart."

    let envelope = {|
        date = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
        version = 2
        info = info
        infoSegments = infoSegments
        topics = withData
    |}

    let writeOptions = JsonSerializerOptions(WriteIndented = true)
    writeOptions.DefaultIgnoreCondition <- JsonIgnoreCondition.WhenWritingNull
    let json = JsonSerializer.Serialize(envelope, writeOptions)

    return (json, "topics.json", "Daily Sports Topics", List.length withData)
}

[<EntryPoint>]
let main args =
    let parser = ArgumentParser.Create<CliArgs>(programName = "fastbreak-daily")

    try
        let results = parser.ParseCommandLine(inputs = args, raiseOnUsage = true)

        if results.Contains Version then
            printfn "Fastbreak Daily v2.0.0"
            exit 0

        if results.Contains EnrichTopics then
            let topicsJson =
                results.TryGetResult TopicsJson
                |> Option.defaultWith (fun () ->
                    failwith "--topics-json is required for enrich-topics")
            let count =
                Fastbreak.Daily.V2.TopicEnricher.enrichTopics topicsJson
                |> Async.RunSynchronously
            printfn "Done! Enriched %d topics" count
            0
        elif results.Contains EnrichDataPoints then
            let enrichedJson =
                results.TryGetResult EnrichedJson
                |> Option.defaultWith (fun () ->
                    failwith "--enriched-json is required for enrich-data-points")
            let count =
                Fastbreak.Daily.V2.DataPointEnricher.enrichWithDataPoints enrichedJson
                |> Async.RunSynchronously
            printfn "Done! Enriched %d topics with data points" count
            0
        elif results.Contains V2 then
            let outputDir =
                results.TryGetResult OutputDir
                |> Option.defaultValue "./output"
            let count = runV2 outputDir
            printfn "Done! Wrote %d topics to %s" count outputDir
            0
        else
            let (json, fileName, title, count) =
                if results.Contains GenerateAndEnrichTopics then
                    runGenerateAndEnrich () |> Async.RunSynchronously
                else
                    runV1()

            let bucket = Environment.GetEnvironmentVariable("AWS_S3_BUCKET")
            if String.IsNullOrEmpty(bucket) then
                failwith "AWS_S3_BUCKET environment variable is not set"

            let isProd = String.Equals(Environment.GetEnvironmentVariable("PROD"), "true", StringComparison.OrdinalIgnoreCase)
            let s3Key = if isProd then fileName else $"dev/{fileName}"
            let tableName = Environment.GetEnvironmentVariable("AWS_DYNAMODB_TABLE") |> Option.ofObj |> Option.defaultValue "fastbreak-file-timestamps"

            async {
                do! uploadToS3 bucket s3Key json
                do! updateDynamoDB tableName s3Key title
            } |> Async.RunSynchronously

            printfn "Done! Uploaded %d items" count
            0
    with
    | :? ArguParseException as ex ->
        printfn "%s" ex.Message
        1
    | ex ->
        eprintfn "Error: %s" ex.Message
        eprintfn "Stack trace: %s" ex.StackTrace
        1
