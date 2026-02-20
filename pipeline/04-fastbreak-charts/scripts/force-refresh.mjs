#!/usr/bin/env node

/**
 * Force Refresh Script
 *
 * Updates all DynamoDB rows to set updatedAt to current timestamp.
 * This forces the app to re-download all charts.
 *
 * Usage: node force-refresh.mjs
 */

import { DynamoDBClient, ScanCommand, UpdateItemCommand } from "@aws-sdk/client-dynamodb";

const TABLE_NAME = process.env.DYNAMODB_TABLE || "fastbreak-file-timestamps";
const REGION = process.env.AWS_REGION || "us-east-1";

const client = new DynamoDBClient({ region: REGION });

async function forceRefreshAll() {
  console.log(`\n📦 Force Refresh - Table: ${TABLE_NAME}`);
  console.log("=".repeat(50));

  // Scan all items
  console.log("\n🔍 Scanning all items...");
  const scanCommand = new ScanCommand({
    TableName: TABLE_NAME,
  });

  const response = await client.send(scanCommand);
  const items = response.Items || [];

  console.log(`   Found ${items.length} items\n`);

  if (items.length === 0) {
    console.log("⚠️  No items found in table");
    return;
  }

  // Get current timestamp in ISO format
  const now = new Date().toISOString();
  console.log(`⏰ Setting updatedAt to: ${now}\n`);

  // Update each item
  let successCount = 0;
  let errorCount = 0;

  for (const item of items) {
    const fileKey = item.file_key?.S;

    if (!fileKey) {
      console.log(`   ⚠️  Skipping item with missing file_key`);
      errorCount++;
      continue;
    }

    try {
      const updateCommand = new UpdateItemCommand({
        TableName: TABLE_NAME,
        Key: {
          file_key: { S: fileKey },
        },
        UpdateExpression: "SET updatedAt = :now",
        ExpressionAttributeValues: {
          ":now": { S: now },
        },
      });

      await client.send(updateCommand);
      console.log(`   ✓ ${fileKey}`);
      successCount++;
    } catch (error) {
      console.log(`   ✗ ${fileKey}: ${error.message}`);
      errorCount++;
    }
  }

  console.log("\n" + "=".repeat(50));
  console.log(`✅ Updated: ${successCount}`);
  if (errorCount > 0) {
    console.log(`❌ Errors: ${errorCount}`);
  }
  console.log(`\n🚀 Apps will now re-download all charts on next sync!\n`);
}

// Run
forceRefreshAll().catch((error) => {
  console.error("\n❌ Error:", error.message);
  process.exit(1);
});
