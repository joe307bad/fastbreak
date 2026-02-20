#!/bin/bash

# Record all Android demos in sequence
# Usage: OUTPUT_DIR=/path/to/output ./scripts/record-all-demos.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="${OUTPUT_DIR:-$SCRIPT_DIR/../screenshots/demos}"

# Convert to absolute path
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"

# All available demo tests
DEMOS=(
    "testDemo_PinchToZoomAndPan"
    "testDemo_ManageTeamsAndFilter"
    "testDemo_HighlightingDataPoints"
    "testDemo_RefreshButton"
    "testDemo_NavigateChartsAndShowInfo"
    "testDemo_Week18MatchupWorksheet"
    "testDemo_DivisionalRoundShareWalkthrough"
)

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     Recording All Demos (${#DEMOS[@]} total)     ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""
echo -e "Output directory: ${GREEN}$OUTPUT_DIR${NC}"
echo ""

mkdir -p "$OUTPUT_DIR"

SUCCESSFUL=0
FAILED=0

for i in "${!DEMOS[@]}"; do
    DEMO="${DEMOS[$i]}"
    NUM=$((i + 1))

    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}[$NUM/${#DEMOS[@]}] Recording: $DEMO${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    if TEST_METHOD="$DEMO" OUTPUT_DIR="$OUTPUT_DIR" "$SCRIPT_DIR/record-android-demo.sh"; then
        echo -e "${GREEN}✓ Completed: $DEMO${NC}"
        SUCCESSFUL=$((SUCCESSFUL + 1))
    else
        echo -e "${RED}✗ Failed: $DEMO${NC}"
        FAILED=$((FAILED + 1))
    fi

    echo ""
done

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║           All Demos Complete           ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""
echo -e "Successful: ${GREEN}$SUCCESSFUL${NC}"
echo -e "Failed:     ${RED}$FAILED${NC}"
echo -e "Output:     ${GREEN}$OUTPUT_DIR${NC}"
echo ""
ls -lh "$OUTPUT_DIR"/*.gif 2>/dev/null || echo "No GIFs found"
