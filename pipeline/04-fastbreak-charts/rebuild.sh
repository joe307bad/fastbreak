#!/bin/bash

echo "Stopping and removing existing container..."
docker stop fastbreak-charts 2>/dev/null
docker rm fastbreak-charts 2>/dev/null

echo "Building new image..."
docker build --platform=linux/arm64 -t fastbreak-charts .

echo "Running new container..."
docker run -d \
  -v /Users/joebad/Source/fastbreak/server/nginx/static:/app/output \
  --name fastbreak-charts \
  fastbreak-charts

echo "Done! View logs with: docker logs -f fastbreak-charts"
