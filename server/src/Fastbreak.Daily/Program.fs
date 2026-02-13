open System
open System.Text.Json
open Amazon.S3
open Amazon.S3.Model
open Amazon.DynamoDBv2
open Amazon.DynamoDBv2.Model
open Fastbreak.Daily.Main

let uploadToS3 (bucket: string) (key: string) (json: string) = async {
    use client = new AmazonS3Client()
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
    use client = new AmazonDynamoDBClient()
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

        let output = {|
            date = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
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
