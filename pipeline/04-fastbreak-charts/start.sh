#!/bin/bash
set +e  # Don't exit on errors

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# RUN_MODE options:
#   all (default) - Run startup, daily, weekly scripts + Fastbreak.Daily + cron
#   daily-only    - Run only Fastbreak.Daily (topics generation)
#   scripts-only  - Run only R scripts (no Fastbreak.Daily)
#   cron-only     - Skip startup, just start cron daemon
RUN_MODE="${RUN_MODE:-all}"

# Optional sport filter (e.g. mlb) — only run {sport}__*.R scripts
SPORT="${SPORT:-}"

# When set, exit after running scripts (do not start cron daemon)
EXIT_AFTER_SCRIPTS=0

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --only-fastbreak-daily|--daily-only)
      RUN_MODE="daily-only"
      shift
      ;;
    --scripts-only)
      RUN_MODE="scripts-only"
      shift
      ;;
    --cron-only)
      RUN_MODE="cron-only"
      shift
      ;;
    --sport)
      if [ -z "${2:-}" ]; then
        echo "Error: --sport requires a value (e.g. mlb)"
        exit 1
      fi
      SPORT="$2"
      shift 2
      ;;
    --sport=*)
      SPORT="${1#*=}"
      shift
      ;;
    --once|--exit)
      EXIT_AFTER_SCRIPTS=1
      shift
      ;;
    --help|-h)
      echo "Usage: $0 [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --only-fastbreak-daily  Run only Fastbreak.Daily and exit"
      echo "  --scripts-only          Run only R scripts, skip Fastbreak.Daily"
      echo "  --sport <name>          Only run <name>__*.R scripts (e.g. mlb)"
      echo "  --once, --exit          Exit after scripts (do not start cron)"
      echo "  --cron-only             Skip startup scripts, just run cron"
      echo "  --help, -h              Show this help message"
      echo ""
      echo "Environment variables:"
      echo "  RUN_MODE=all|daily-only|scripts-only|cron-only"
      echo "  SPORT=mlb|nba|nfl|nhl|cbb"
      echo "  ENV=PROD|DEV             Controls S3 prefix (prod/ vs dev/)"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

echo ""
echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${BLUE}                  R Cron Scheduler Starting                  ${NC}"
echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "  ${CYAN}Run mode: ${BOLD}$RUN_MODE${NC}"
if [ -n "$SPORT" ]; then
  echo -e "  ${CYAN}Sport filter: ${BOLD}${SPORT}__*.R${NC}"
fi
if [ "$EXIT_AFTER_SCRIPTS" -eq 1 ]; then
  echo -e "  ${CYAN}Exit after scripts: ${BOLD}yes${NC}"
fi
echo ""

# Export environment variables for cron jobs (restricted permissions)
# Include PATH so cron can find aws cli and other tools
echo "export PATH=\"$PATH\"" > /app/env.sh
printenv | grep -E "^(AWS_|ENV|GEMINI_)" >> /app/env.sh
sed -i '/^AWS_\|^ENV\|^GEMINI_/s/^/export /' /app/env.sh
chmod 600 /app/env.sh

# Handle daily-only mode - just run Fastbreak.Daily and exit
if [ "$RUN_MODE" = "daily-only" ]; then
  echo -e "${BOLD}${CYAN}▶ Running Fastbreak.Daily (generate-and-enrich-topics)...${NC}"
  echo -e "${CYAN}────────────────────────────────────────${NC}"
  if /app/Fastbreak.Daily generate-and-enrich-topics; then
    echo -e "${GREEN}  ✓ Fastbreak.Daily completed successfully${NC}"
    exit 0
  else
    echo -e "${RED}  ✗ Fastbreak.Daily failed${NC}"
    exit 1
  fi
fi

# Handle cron-only mode - skip startup scripts
if [ "$RUN_MODE" = "cron-only" ]; then
  echo -e "${YELLOW}Skipping startup scripts (cron-only mode)${NC}"
  echo ""
  cron
  echo -e "${GREEN}Container is running. Watching for cron activity...${NC}"
  tail -f /var/log/cron.log 2>/dev/null || while true; do sleep 3600; done
  exit 0
fi

run_scripts() {
  local dir=$1
  local label=$2
  local count=0
  local success=0
  local failed=0
  local pattern="*.R"

  if [ -n "$SPORT" ]; then
    pattern="${SPORT}__*.R"
  fi

  echo -e "${BOLD}${CYAN}▶ Running $label scripts...${NC}"
  if [ -n "$SPORT" ]; then
    echo -e "${CYAN}  (filter: $pattern)${NC}"
  fi
  echo -e "${CYAN}────────────────────────────────────────${NC}"

  # shellcheck disable=SC2086
  for script in "$dir"/$pattern; do
    if [ -f "$script" ]; then
      count=$((count + 1))
      script_name=$(basename "$script")
      echo -e "${YELLOW}  ⏳ $script_name${NC}"

      output=$(Rscript "$script" 2>&1)
      exit_code=$?

      if [ $exit_code -eq 0 ]; then
        success=$((success + 1))
        echo -e "${GREEN}  ✓ $script_name completed successfully${NC}"
        # Show upload confirmation if present
        if echo "$output" | grep -q "Uploaded to S3"; then
          s3_path=$(echo "$output" | grep "Uploaded to S3" | tail -1)
          echo -e "${GREEN}    └─ $s3_path${NC}"
        fi
      else
        failed=$((failed + 1))
        echo -e "${RED}  ✗ $script_name failed${NC}"
        echo -e "${RED}    └─ Error output:${NC}"
        echo "$output" | tail -5 | sed 's/^/       /'
      fi
      echo ""
    fi
  done

  if [ $count -eq 0 ]; then
    echo -e "  ${YELLOW}No scripts found in $dir matching $pattern${NC}"
  else
    echo -e "${CYAN}────────────────────────────────────────${NC}"
    echo -e "  ${BOLD}Results:${NC} ${GREEN}$success passed${NC} | ${RED}$failed failed${NC} | Total: $count"
  fi
  echo ""
}

# Run scripts on startup
run_scripts "/app/startup" "startup"
run_scripts "/app/daily" "daily"
run_scripts "/app/weekly" "weekly"

# Run Fastbreak.Daily to generate topics (v2 pipeline) - skip in scripts-only mode
if [ "$RUN_MODE" != "scripts-only" ]; then
  echo -e "${BOLD}${CYAN}▶ Running Fastbreak.Daily (generate-and-enrich-topics)...${NC}"
  echo -e "${CYAN}────────────────────────────────────────${NC}"
  if /app/Fastbreak.Daily generate-and-enrich-topics; then
    echo -e "${GREEN}  ✓ Fastbreak.Daily completed successfully${NC}"
  else
    echo -e "${RED}  ✗ Fastbreak.Daily failed${NC}"
  fi
  echo ""
else
  echo -e "${YELLOW}Skipping Fastbreak.Daily (scripts-only mode)${NC}"
  echo ""
fi

echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${BLUE}                    Startup Complete                         ${NC}"
echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════${NC}"
echo ""

if [ "$EXIT_AFTER_SCRIPTS" -eq 1 ]; then
  echo -e "${GREEN}Scripts finished. Exiting (--once).${NC}"
  exit 0
fi

echo -e "  ${BOLD}Schedule:${NC}"
echo -e "  • Daily scripts:  ${CYAN}Every day at midnight${NC}"
echo -e "  • Weekly scripts: ${CYAN}Every Sunday at midnight${NC}"
echo ""
echo -e "${YELLOW}Watching cron log...${NC}"
echo ""

# Start cron and keep container alive
cron

# Keep the container running forever
echo -e "${GREEN}Container is running. Watching for cron activity...${NC}"
tail -f /var/log/cron.log 2>/dev/null || while true; do sleep 3600; done
