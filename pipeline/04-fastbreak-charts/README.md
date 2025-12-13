# R Cron Scheduler

Simple Docker-based R script scheduler using cron.

## Structure

- `daily/` - R scripts that run every day at midnight
- `weekly/` - R scripts that run every Sunday at midnight
- Output mounts to `/Users/joebad/Source/fastbreak/server/nginx/static`

## Build

```bash
docker build -t fastbreak-charts .
```

## Run

```bash
docker run -d --env-file .env --name fastbreak-charts fastbreak-charts
```

## How It Works

- Cron runs daily scripts at `0 0 * * *` (midnight daily)
- Cron runs weekly scripts at `0 0 * * 0` (midnight Sunday)
- All R scripts in respective folders execute automatically
- Scripts run immediately on container startup
- JSON output saved to `/app/output` (mounted to `/Users/joebad/Source/fastbreak/server/nginx/static`)
