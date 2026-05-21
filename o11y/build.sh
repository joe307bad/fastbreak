#!/bin/bash
set -e

cd "$(dirname "$0")"

# Check for .env file
if [ ! -f .env ]; then
    echo "Error: .env file not found"
    echo "Copy .env.example to .env and fill in your secrets"
    exit 1
fi

# Source secrets and export them for envsubst
set -a
source .env
set +a

# Validate required vars
if [ -z "$API_KEY" ] || [ -z "$CADDY_ADMIN_USER" ] || [ -z "$CADDY_ADMIN_PASS_HASH" ]; then
    echo "Error: Missing required environment variables"
    echo "Required: API_KEY, CADDY_ADMIN_USER, CADDY_ADMIN_PASS_HASH"
    exit 1
fi

# Generate Caddyfile from template
echo "Generating Caddyfile from template..."
envsubst '${API_KEY} ${CADDY_ADMIN_USER} ${CADDY_ADMIN_PASS_HASH}' < Caddyfile.template > Caddyfile

# Get current version and increment
CURRENT_VERSION=$(grep -o 'caddy-v[0-9]*' fly.toml | head -1 | grep -o '[0-9]*')
NEW_VERSION=$((CURRENT_VERSION + 1))
NEW_TAG="caddy-v${NEW_VERSION}"

echo "Building and pushing ${NEW_TAG}..."

# Authenticate and build
fly auth docker
docker buildx build --platform linux/amd64 -t "registry.fly.io/fastbreak-o11y:${NEW_TAG}" --push .

# Update config files
sed -i '' "s/caddy-v${CURRENT_VERSION}/${NEW_TAG}/g" fly.toml machine-config.json

echo "Updated configs to ${NEW_TAG}"
echo ""
echo "To deploy: fly deploy -a fastbreak-o11y"
