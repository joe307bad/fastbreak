import { DynamoDBClient, ScanCommand } from "@aws-sdk/client-dynamodb";
import { unmarshall } from "@aws-sdk/util-dynamodb";

const client = new DynamoDBClient({});
const TABLE_NAME = process.env.DYNAMODB_TABLE || "fastbreak-file-timestamps";
const NAMESPACE = "scheduler-o11y";

// Returns every script-run record the pipeline wrote under the scheduler-o11y
// namespace (one row per script per env, overwritten on each run).
export const handler = async () => {
  try {
    const items = [];
    let ExclusiveStartKey;
    do {
      const response = await client.send(
        new ScanCommand({
          TableName: TABLE_NAME,
          FilterExpression: "#ns = :ns",
          ExpressionAttributeNames: { "#ns": "namespace" },
          ExpressionAttributeValues: { ":ns": { S: NAMESPACE } },
          ExclusiveStartKey,
        })
      );
      for (const item of response.Items || []) {
        items.push(unmarshall(item));
      }
      ExclusiveStartKey = response.LastEvaluatedKey;
    } while (ExclusiveStartKey);

    items.sort((a, b) => String(b.finishedAt || "").localeCompare(String(a.finishedAt || "")));

    return {
      statusCode: 200,
      headers: {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
      },
      body: JSON.stringify({
        namespace: NAMESPACE,
        generatedAt: new Date().toISOString(),
        count: items.length,
        items,
      }),
    };
  } catch (error) {
    console.error("Error scanning DynamoDB:", error);
    return {
      statusCode: 500,
      headers: {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
      },
      body: JSON.stringify({ error: "Failed to fetch scheduler-o11y records" }),
    };
  }
};
