#!/bin/bash

echo "Stopping and removing existing container..."
docker stop fastbreak-charts 2>/dev/null
docker rm fastbreak-charts 2>/dev/null

echo "Building new image..."
docker build -t fastbreak-charts .

echo "Running new container..."
docker run -d \
  --name fastbreak-charts \
  --env-file .env \
  fastbreak-charts

echo "Done! View logs with: docker logs -f fastbreak-charts"
