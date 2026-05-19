#!/bin/bash
set -e

# Run the fastbreak-charts container in PROD mode
# This will upload charts to the prod/ prefix in S3
#
# Usage:
#   ./prod.sh                        # Run all scripts + Fastbreak.Daily
#   ./prod.sh --only-fastbreak-daily # Run only Fastbreak.Daily
#   ./prod.sh --scripts-only         # Run only R scripts
#   ./prod.sh --help                 # Show help

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Source local .env for AWS credentials
if [ -f .env ]; then
  source .env
else
  echo "Error: .env file not found"
  exit 1
fi

# Always rebuild Fastbreak.Daily to ensure latest code
echo "Building Fastbreak.Daily..."
cd ../../server/src/Fastbreak.Daily
dotnet publish -c Release -r linux-x64 --self-contained true -p:PublishSingleFile=true -o ../../../pipeline/04-fastbreak-charts
cd "$SCRIPT_DIR"

# Build the Docker image
echo "Building Docker image..."
docker build -t fastbreak-charts .

# Run in PROD mode, passing any command-line arguments to start.sh
echo "Running container in PROD mode..."
docker run --rm \
  -e ENV=PROD \
  -e AWS_ACCESS_KEY_ID \
  -e AWS_SECRET_ACCESS_KEY \
  -e AWS_DEFAULT_REGION \
  -e AWS_S3_BUCKET \
  -e AWS_DYNAMODB_TABLE \
  -e GEMINI_API_KEY \
  fastbreak-charts /app/start.sh "$@"
