#!/bin/bash
# Runs one pipeline job and records the outcome in DynamoDB under the
# scheduler-o11y namespace so the web site's /diagnostics page can show it.
#
# Usage:
#   run-script.sh <schedule> <path/to/script.R>
#   run-script.sh <schedule> <command> [args...]
#
# <schedule> is a label such as startup, daily, weekly, or topics.
# A *.R script is run with Rscript; anything else is run as-is.
#
# One row is written per script per env (file_key scheduler-o11y/<env>/<name>)
# and overwritten on every run, holding status, timing, and on failure the
# tail of the output. The write is best-effort: it never changes the exit code.
#
# Output from the job is passed through to stdout so callers can still grep it.

set +e

schedule="${1:?usage: run-script.sh <schedule> <script|command> [args...]}"
shift
target="${1:?usage: run-script.sh <schedule> <script|command> [args...]}"
shift

if [[ "$target" == *.R ]]; then
  name=$(basename "$target")
  # cron runs with a minimal PATH; prefer the image's Rscript when present
  rscript=$(command -v /usr/local/bin/Rscript || command -v Rscript)
  cmd=("$rscript" "$target" "$@")
else
  name=$(basename "$target")
  cmd=("$target" "$@")
fi

env_name=$(echo "${ENV:-DEV}" | tr '[:upper:]' '[:lower:]')
table="${AWS_DYNAMODB_TABLE:-fastbreak-file-timestamps}"
file_key="scheduler-o11y/${env_name}/${name}"

now_epoch() {
  if [ -n "${EPOCHREALTIME:-}" ]; then
    echo "$EPOCHREALTIME"
  else
    date +%s
  fi
}

started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
start=$(now_epoch)
output_file=$(mktemp)

"${cmd[@]}" 2>&1 | tee "$output_file"
exit_code=${PIPESTATUS[0]}

end=$(now_epoch)
finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
duration=$(awk -v s="$start" -v e="$end" 'BEGIN { printf "%.1f", e - s }')

if [ "$exit_code" -eq 0 ]; then
  status="success"
else
  status="failed"
fi

item_file=$(mktemp)
# python3 is present wherever the AWS CLI is (it is pip-installed); use it to
# build the item so error text is JSON-escaped correctly.
FILE_KEY="$file_key" NAME="$name" SCHEDULE="$schedule" ENV_NAME="$env_name" \
STATUS="$status" EXIT_CODE="$exit_code" STARTED_AT="$started_at" FINISHED_AT="$finished_at" \
DURATION="$duration" OUTPUT_FILE="$output_file" \
python3 - > "$item_file" <<'PY'
import json, os

def s(v): return {"S": v}
def n(v): return {"N": str(v)}

item = {
    "file_key":        s(os.environ["FILE_KEY"]),
    "namespace":       s("scheduler-o11y"),
    "script":          s(os.environ["NAME"]),
    "schedule":        s(os.environ["SCHEDULE"]),
    "env":             s(os.environ["ENV_NAME"]),
    "status":          s(os.environ["STATUS"]),
    "exitCode":        n(os.environ["EXIT_CODE"]),
    "startedAt":       s(os.environ["STARTED_AT"]),
    "finishedAt":      s(os.environ["FINISHED_AT"]),
    "durationSeconds": n(os.environ["DURATION"]),
    "updatedAt":       s(os.environ["FINISHED_AT"]),
}

if os.environ["STATUS"] != "success":
    try:
        with open(os.environ["OUTPUT_FILE"], encoding="utf-8", errors="replace") as f:
            lines = f.read().splitlines()
    except OSError:
        lines = []
    tail = "\n".join(lines[-40:]).strip()
    if len(tail) > 4000:
        tail = "…" + tail[-4000:]
    item["error"] = s(tail or f"exit code {os.environ['EXIT_CODE']} with no output")

print(json.dumps(item))
PY

if aws dynamodb put-item --table-name "$table" --item "file://$item_file" >/dev/null 2>&1; then
  echo "[scheduler-o11y] $file_key -> $status (${duration}s)"
else
  echo "[scheduler-o11y] WARNING: failed to record $file_key in DynamoDB (non-fatal)"
fi

# Also refresh the suite-level row (scheduler-o11y/<env>/suite) that says when
# the next daily and weekly cron runs are due, so /diagnostics can show it.
# The times come from the installed crontab so they cannot drift from it.
schedule_file=$(mktemp)
ENV_NAME="$env_name" NAME="$name" FINISHED_AT="$finished_at" CRONTAB_FILE="${CRONTAB_FILE:-/etc/cron.d/r-cron}" \
python3 - > "$schedule_file" <<'PY'
import json, os, re
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

TZ_NAME = os.environ.get("TZ") or "America/New_York"
tz = ZoneInfo(TZ_NAME)
now = datetime.now(tz)

# Defaults match crontab; overridden by whatever the crontab actually says.
crons = {"daily": "0 6 * * *", "weekly": "0 7 * * 0"}
try:
    with open(os.environ["CRONTAB_FILE"]) as f:
        for line in f:
            m = re.match(r"^\s*(\S+\s+\S+\s+\S+\s+\S+\s+\S+)\s+.*run-script\.sh\s+(daily|weekly)\b", line)
            if m:
                crons[m.group(2)] = m.group(1)
except OSError:
    pass

def next_run(cron):
    minute, hour, _, _, dow = cron.split()
    minute, hour = int(minute), int(hour)
    days = None if dow == "*" else {int(d) % 7 for d in dow.split(",")}  # cron: 0 = Sunday
    candidate = now.replace(hour=hour, minute=minute, second=0, microsecond=0)
    for _ in range(8):
        # Python weekday(): Monday = 0; cron: Sunday = 0
        cron_dow = (candidate.weekday() + 1) % 7
        if candidate > now and (days is None or cron_dow in days):
            return candidate
        candidate = (candidate + timedelta(days=1)).replace(hour=hour, minute=minute)
    return candidate

def s(v): return {"S": v}
iso = lambda d: d.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

print(json.dumps({
    "file_key":          s(f"scheduler-o11y/{os.environ['ENV_NAME']}/suite"),
    "namespace":         s("scheduler-o11y"),
    "kind":              s("schedule"),
    "env":               s(os.environ["ENV_NAME"]),
    "timezone":          s(TZ_NAME),
    "dailyCron":         s(crons["daily"]),
    "weeklyCron":        s(crons["weekly"]),
    "nextDailyRunAt":    s(iso(next_run(crons["daily"]))),
    "nextWeeklyRunAt":   s(iso(next_run(crons["weekly"]))),
    "lastJobScript":     s(os.environ["NAME"]),
    "lastJobFinishedAt": s(os.environ["FINISHED_AT"]),
    "updatedAt":         s(os.environ["FINISHED_AT"]),
}))
PY

if ! aws dynamodb put-item --table-name "$table" --item "file://$schedule_file" >/dev/null 2>&1; then
  echo "[scheduler-o11y] WARNING: failed to refresh the suite schedule row (non-fatal)"
fi

rm -f "$output_file" "$item_file" "$schedule_file"
exit "$exit_code"
