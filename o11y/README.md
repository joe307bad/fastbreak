# Fastbreak Observability (o11y)

OpenTelemetry Collector for receiving telemetry from the Fastbreak mobile app.

## Architecture

```
Mobile App (iOS/Android)
        │
        ▼
  OTEL Collector (Fly.io)
        │
        ▼
   SigNoz Cloud
```

## Setup

### 1. Create SigNoz Cloud Account

1. Sign up at [SigNoz Cloud](https://signoz.io/teams/)
2. Get your ingestion key from Settings > Ingestion Key
3. Note your region endpoint (e.g., `ingest.us.signoz.cloud:443`)

### 2. Deploy to Fly.io

```bash
cd o11y

# Create the app
fly apps create fastbreak-otel

# Generate a secure auth token (use any secure random string)
export OTEL_AUTH_TOKEN=$(openssl rand -hex 32)
echo "Save this token for gradle.properties: $OTEL_AUTH_TOKEN"

# Set secrets
fly secrets set SIGNOZ_INGESTION_KEY="your-signoz-ingestion-key"
fly secrets set SIGNOZ_ENDPOINT="ingest.us.signoz.cloud:443"
fly secrets set OTEL_AUTH_TOKEN="$OTEL_AUTH_TOKEN"

# Deploy
fly deploy
```

### 3. Configure Mobile App

Update `kmp/gradle.properties`:

```properties
otel_endpoint=https://fastbreak-otel.fly.dev
otel_auth_token=<the-token-from-step-2>
```

**Security Note**: The `otel_auth_token` is compiled into the app binary. While this prevents casual abuse, a determined attacker could extract it. For a mobile app sending anonymized telemetry, this is acceptable. For sensitive data, consider additional measures like certificate pinning.

## Local Development

Run the collector locally:

```bash
docker build -t fastbreak-otel .
docker run -p 4317:4317 -p 4318:4318 \
  -e SIGNOZ_INGESTION_KEY="your-key" \
  -e SIGNOZ_ENDPOINT="ingest.us.signoz.cloud:443" \
  fastbreak-otel
```

## Endpoints

- **OTLP HTTP**: `https://fastbreak-otel.fly.dev/v1/traces`
- **OTLP gRPC**: `fastbreak-otel.fly.dev:4317`
- **Health Check**: `https://fastbreak-otel.fly.dev:13133/health`
