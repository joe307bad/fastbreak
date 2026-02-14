open System
open System.Text.Json
open Amazon
open Amazon.S3
open Amazon.S3.Model
open Amazon.DynamoDBv2
open Amazon.DynamoDBv2.Model
open Fastbreak.Daily.Main

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

[<EntryPoint>]
let main _ =
    try
        let narratives = run() |> Async.RunSynchronously

        // Description as text segments with links
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

        // Get environment variables
        let bucket = Environment.GetEnvironmentVariable("AWS_S3_BUCKET")
        if String.IsNullOrEmpty(bucket) then
            failwith "AWS_S3_BUCKET environment variable is not set"

        let isProd = String.Equals(Environment.GetEnvironmentVariable("PROD"), "true", StringComparison.OrdinalIgnoreCase)
        let s3Key = if isProd then "topics.json" else "dev/topics.json"
        let tableName = Environment.GetEnvironmentVariable("AWS_DYNAMODB_TABLE") |> Option.ofObj |> Option.defaultValue "fastbreak-file-timestamps"
        let title = "Daily Sports Narratives"

        // Upload to S3 and update DynamoDB
        async {
            do! uploadToS3 bucket s3Key json
            do! updateDynamoDB tableName s3Key title
        } |> Async.RunSynchronously

        printfn "Done! Uploaded %d narratives" (List.length narratives)
        0
    with
    | ex ->
        eprintfn "Error: %s" ex.Message
        eprintfn "Stack trace: %s" ex.StackTrace
        1
