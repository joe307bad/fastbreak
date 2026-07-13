#!/bin/bash
set -e

# Run only MLB-namespaced R charts against PROD (S3 prod/ + DynamoDB timestamps).
#
# Usage:
#   ./prod-mlb.sh              # All mlb__*.R in startup/daily/weekly → prod S3
#   ./prod-mlb.sh --help

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

exec ./prod.sh --scripts-only --sport mlb --once "$@"
