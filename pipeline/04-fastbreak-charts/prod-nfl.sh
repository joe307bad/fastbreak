#!/bin/bash
set -e

# Run only NFL-namespaced R charts against PROD (S3 prod/ + DynamoDB timestamps).
#
# Usage:
#   ./prod-nfl.sh              # All nfl__*.R in startup/daily/weekly → prod S3
#   ./prod-nfl.sh --help

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

exec ./prod.sh --scripts-only --sport nfl --once "$@"
