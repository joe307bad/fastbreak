import { DynamoDBClient, ScanCommand } from "@aws-sdk/client-dynamodb";
import { unmarshall } from "@aws-sdk/util-dynamodb";

const client = new DynamoDBClient({});
const TABLE_NAME = process.env.DYNAMODB_TABLE || "fastbreak-file-timestamps";

export const handler = async () => {
  try {
    const command = new ScanCommand({
      TableName: TABLE_NAME,
    });

    const response = await client.send(command);

    const result = {};
    for (const item of response.Items || []) {
      const unmarshalled = unmarshall(item);
      const fileKey = unmarshalled.file_key;
      delete unmarshalled.file_key;
      result[fileKey] = unmarshalled;
    }

    return {
      statusCode: 200,
      headers: {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
      },
      body: JSON.stringify(result),
    };
  } catch (error) {
    console.error("Error scanning DynamoDB:", error);
    return {
      statusCode: 500,
      headers: {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
      },
      body: JSON.stringify({ error: "Failed to fetch registry" }),
    };
  }
};
