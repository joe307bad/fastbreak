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

echo ""
echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${BLUE}                  R Cron Scheduler Starting                  ${NC}"
echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════${NC}"
echo ""

# Export environment variables for cron jobs (restricted permissions)
printenv | grep -E "^(AWS_|PROD)" > /app/env.sh
sed -i 's/^/export /' /app/env.sh
chmod 600 /app/env.sh

run_scripts() {
  local dir=$1
  local label=$2
  local count=0
  local success=0
  local failed=0

  echo -e "${BOLD}${CYAN}▶ Running $label scripts...${NC}"
  echo -e "${CYAN}────────────────────────────────────────${NC}"

  for script in "$dir"/*.R; do
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
    echo -e "  ${YELLOW}No scripts found in $dir${NC}"
  else
    echo -e "${CYAN}────────────────────────────────────────${NC}"
    echo -e "  ${BOLD}Results:${NC} ${GREEN}$success passed${NC} | ${RED}$failed failed${NC} | Total: $count"
  fi
  echo ""
}

# Run scripts on startup
run_scripts "/app/daily" "daily"
run_scripts "/app/weekly" "weekly"

echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${BLUE}                    Startup Complete                         ${NC}"
echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════${NC}"
echo ""
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
