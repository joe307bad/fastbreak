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

## Run diagnostics (scheduler-o11y)

Every job — each R script and `Fastbreak.Daily` — is launched through
`run-script.sh`, both at startup (`start.sh`) and from cron. The wrapper times
the run and writes one row to the DynamoDB table (`AWS_DYNAMODB_TABLE`) under
the `scheduler-o11y` namespace:

- `file_key`: `scheduler-o11y/<env>/<script>` (one row per script, overwritten each run)
- `status`: `success` or `failed`, with `exitCode`
- `startedAt`, `finishedAt`, `durationSeconds`
- `error`: last 40 lines of output, only on failure

The `fastbreak-scheduler-o11y` Lambda (`aws/`) serves these rows at
`https://<cloudfront>/scheduler-o11y`, and the web site's build pulls them into
its static `/diagnostics` page. The registry endpoint filters these rows out.

Because the write happens in the wrapper rather than inside each script, a
script that dies before it can run any code (missing package, syntax error,
OOM) is still recorded as failed.

<!-- release id sync -->
