open System
open System.IO
open System.Collections.Generic
open Amazon.S3
open Amazon.S3.Model
open Amazon.DynamoDBv2
open Amazon.DynamoDBv2.Model

let bucketName = "fb-ch-def"
let dynamoTableName = "fastbreak-file-timestamps"
let s3Key = "dev/topics.json"

let uploadToS3 (json: string) = async {
    use s3Client = new AmazonS3Client(Amazon.RegionEndpoint.USEast1)

    let request = PutObjectRequest()
    request.BucketName <- bucketName
    request.Key <- s3Key
    request.ContentType <- "application/json"
    request.ContentBody <- json

    let! response = s3Client.PutObjectAsync(request) |> Async.AwaitTask
    printfn "Uploaded to S3: %s/%s (Status: %d)" bucketName s3Key (int response.HttpStatusCode)
}

let insertDynamoDbRow () = async {
    use dynamoClient = new AmazonDynamoDBClient(Amazon.RegionEndpoint.USEast1)

    let now = DateTime.UtcNow
    let updatedAt = now.ToString("yyyy-MM-ddTHH:mm:ssZ")
    let title = sprintf "Sports Topics - %s" (now.ToString("yyyy-MM-dd"))

    let item = Dictionary<string, AttributeValue>()
    item.["file_key"] <- AttributeValue(s3Key)
    item.["interval"] <- AttributeValue("daily")
    item.["updatedAt"] <- AttributeValue(updatedAt)
    item.["title"] <- AttributeValue(title)
    item.["type"] <- AttributeValue("topics")

    let request = PutItemRequest()
    request.TableName <- dynamoTableName
    request.Item <- item

    let! response = dynamoClient.PutItemAsync(request) |> Async.AwaitTask
    printfn "Inserted DynamoDB row: %s (Status: %d)" s3Key (int response.HttpStatusCode)
}

[<EntryPoint>]
let main _ =
    try
        printfn "Generating topics..."
        printfn ""

        let result = TopicsGenerator.generateTopics () |> Async.RunSynchronously

        // Upload to S3
        printfn ""
        printfn "Uploading to S3..."
        uploadToS3 result |> Async.RunSynchronously

        // Insert DynamoDB row
        printfn "Inserting DynamoDB row..."
        insertDynamoDbRow () |> Async.RunSynchronously

        printfn ""
        printfn "Done!"

        0
    with
    | ex ->
        eprintfn "Error: %s" ex.Message
        eprintfn "Stack trace: %s" ex.StackTrace
        1
