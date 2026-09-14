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

rm -f "$output_file" "$item_file"
exit "$exit_code"
