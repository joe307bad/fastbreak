# Fastbreak Observability (o11y)

QuestDB + Caddy on Fly.io for mobile app telemetry.

## Architecture

```
Mobile App (iOS/Android)
        │
        ▼ POST /write (X-API-Key header)
   Caddy (TLS + Auth + Rate Limiting)
        │
        ▼
   QuestDB (localhost:9000)
        │
        ▼
   Web Console (basic auth protected)
```

## Security

| Endpoint | Access | Auth |
|----------|--------|------|
| `/write` | Public | `X-API-Key` header |
| Web Console | Protected | Basic auth |
| `/exec` | Protected | Basic auth |

- QuestDB HTTP auth enabled as defense-in-depth
- TLS automatic via Caddy/Fly.io
- Expect mobile API key to leak (can be extracted from app binary)

## Deploy

### 1. Create Fly app and volume

```bash
cd o11y

# Create the app
fly apps create fastbreak-o11y

# Create persistent volume for QuestDB data
fly volumes create questdb_data --size 10 --region iad
```

### 2. Generate credentials

```bash
# API key for mobile app
API_KEY=$(openssl rand -hex 32)
echo "Save this for GitHub secrets: $API_KEY"

# Caddy password hash (enter your password when prompted)
docker run --rm -it caddy:2-alpine caddy hash-password

# QuestDB password
QUESTDB_PASS=$(openssl rand -base64 24)
echo "QuestDB password: $QUESTDB_PASS"
```

### 3. Set Fly secrets

```bash
fly secrets set \
  API_KEY="<your-api-key>" \
  CADDY_ADMIN_USER="admin" \
  CADDY_ADMIN_PASS_HASH="<bcrypt-hash-from-step-2>" \
  QUESTDB_USER="admin" \
  QUESTDB_PASSWORD="<questdb-password>"
```

### 4. Deploy

```bash
fly deploy
```

## Configure Mobile App

GitHub secrets:
- `OTEL_ENDPOINT`: `https://fastbreak-o11y.fly.dev`
- `OTEL_AUTH_TOKEN`: The `API_KEY` from step 2

## Access Web Console

1. Open `https://fastbreak-o11y.fly.dev`
2. Enter basic auth credentials (admin / your password)
3. Use SQL console to query telemetry data

## Example Queries

```sql
-- Recent events
SELECT * FROM app_events ORDER BY timestamp DESC LIMIT 100;

-- Events by type
SELECT event_type, count() FROM app_events GROUP BY event_type;

-- Daily active users
SELECT toStartOfDay(timestamp) as day, count(DISTINCT device_id)
FROM app_events
GROUP BY day
ORDER BY day DESC;
```

## Troubleshooting

```bash
# View logs
fly logs

# SSH into machine
fly ssh console

# Check QuestDB health
fly ssh console -C "curl -s http://localhost:9000/"

# Check Caddy logs
fly logs --process caddy
```

## Cost

~$7/month total:
- Fly.io VM (1GB RAM): ~$5/month
- Fly.io Volume (10GB): ~$2/month
