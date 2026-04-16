open System
open System.Text.Json
open Argu
open Amazon
open Amazon.S3
open Amazon.S3.Model
open Amazon.DynamoDBv2
open Amazon.DynamoDBv2.Model
open Fastbreak.Daily.Main
open Fastbreak.Daily.V2NarrativeGenerator

type CliArgs =
    | [<CustomCommandLine("v1"); CliPrefix(CliPrefix.None)>] V1
    | [<CustomCommandLine("v2"); CliPrefix(CliPrefix.None)>] V2
    | Version
    interface IArgParserTemplate with
        member this.Usage =
            match this with
            | V1 -> "run the v1 narrative pipeline (default)"
            | V2 -> "run the v2 topics pipeline"
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

let runV2 () =
    let topics = Fastbreak.Daily.V2Main.run() |> Async.RunSynchronously

    let output = {|
        date = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
        version = 2
        topics = topics
    |}

    let options = JsonSerializerOptions(WriteIndented = true)
    let json = JsonSerializer.Serialize(output, options)
    (json, "topics-v2.json", "Daily Sports Topics V2", List.length topics)

[<EntryPoint>]
let main args =
    let parser = ArgumentParser.Create<CliArgs>(programName = "fastbreak-daily")

    try
        let results = parser.ParseCommandLine(inputs = args, raiseOnUsage = true)

        let (json, fileName, title, count) =
            match results.GetAllResults() with
            | [V2] -> runV2()
            | [Version] ->
                printfn "Fastbreak Daily v2.0.0"
                exit 0
            | [] | [V1] -> runV1()
            | _ ->
                printfn "Invalid combination of arguments"
                printfn "%s" (parser.PrintUsage())
                exit 1

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
