# Fastbreak API

This is the F# API for the Fastbreak application, deployed on Fly.io.

## Deployment Setup

### Prerequisites

1. Install the Fly CLI: `curl -L https://fly.io/install.sh | sh`
2. Create a Fly.io account: `flyctl auth signup`
3. Create a new app: `flyctl apps create fastbreak-api`

### Environment Variables

The following environment variables need to be set in Fly.io:

```bash
flyctl secrets set MONGO_USER="your_mongo_user"
flyctl secrets set MONGO_PASS="your_mongo_password"
flyctl secrets set MONGO_IP="your_mongo_ip"
flyctl secrets set MONGO_DB="your_mongo_database"
flyctl secrets set ENABLE_DAILY_JOB="1"
flyctl secrets set ENABLE_SCHEDULE_PULLER="1"
```

### GitHub Secrets

Add the following secret to your GitHub repository:

- `FLY_API_TOKEN`: Your Fly.io API token (get it with `flyctl auth token`)

### Manual Deployment

To deploy manually:

```bash
cd api
flyctl deploy
```

### Automatic Deployment

The API automatically deploys when:
- Changes are pushed to the `main` branch in the `/api` folder
- A PR with changes to `/api` is merged into `main`

### Health Check

The API includes a health endpoint at `/` that returns:

```json
{
  "status": "healthy",
  "timestamp": "2023-01-01T00:00:00.000Z"
}
```

### Cost Optimization

The `fly.toml` configuration is optimized for minimal costs:
- Uses `shared-cpu-1x` (cheapest VM)
- 256MB RAM
- Auto-stop/start machines when not in use
- Scales to zero when idle
