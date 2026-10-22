#!/bin/bash

# Capture App Store screenshots from the iOS simulator, and optionally upload
# them to App Store Connect.
#
# Usage:
#   ./scripts/capture-ios-screenshots.sh            # capture into iosApp/fastlane/screenshots
#   ./scripts/capture-ios-screenshots.sh --upload   # capture, then upload to App Store Connect
#   ./scripts/capture-ios-screenshots.sh --open     # capture, then open the output folder
#
# Capturing is local and repeatable. Uploading is not, so it is opt-in: the
# --upload path replaces the screenshots on the current editable App Store
# version. It does not submit anything for review.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
IOS_DIR="$SCRIPT_DIR/../iosApp"
OUTPUT_DIR="$IOS_DIR/fastlane/screenshots"

GREEN='\033[0;32m'; BLUE='\033[0;34m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

UPLOAD=0
OPEN_AFTER=0
for arg in "$@"; do
    case "$arg" in
        --upload) UPLOAD=1 ;;
        --open) OPEN_AFTER=1 ;;
        --help|-h) sed -n '3,14p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo -e "${RED}Unknown option: $arg${NC}"; exit 1 ;;
    esac
done

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║      iOS App Store Screenshots         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# --- Prerequisites ----------------------------------------------------------

if ! xcodebuild -version >/dev/null 2>&1; then
    echo -e "${RED}✗ Xcode command line tools not available${NC}"
    exit 1
fi

# fastlane has to be runnable. A Homebrew fastlane on a Ruby whose bundler does
# not match will fail here rather than halfway through a simulator run.
FASTLANE_CMD=""
if (cd "$IOS_DIR" && bundle exec fastlane --version >/dev/null 2>&1); then
    FASTLANE_CMD="bundle exec fastlane"
elif fastlane --version >/dev/null 2>&1; then
    FASTLANE_CMD="fastlane"
else
    echo -e "${RED}✗ fastlane could not run${NC}"
    echo ""
    echo "  Tried 'bundle exec fastlane' and 'fastlane', both failed."
    echo "  Most often this is a Ruby/bundler mismatch. From kmp/iosApp:"
    echo ""
    echo -e "    ${YELLOW}gem install bundler && bundle install${NC}"
    echo ""
    echo "  Or install fastlane into a Ruby you control (rbenv/asdf) rather"
    echo "  than using the system Ruby."
    exit 1
fi
echo -e "  fastlane: ${GREEN}$FASTLANE_CMD${NC}"

if [ "$UPLOAD" -eq 1 ]; then
    MISSING=""
    for var in APP_STORE_CONNECT_API_KEY_KEY_ID APP_STORE_CONNECT_API_KEY_ISSUER_ID APP_STORE_CONNECT_API_KEY_KEY; do
        if [ -z "${!var:-}" ]; then MISSING="$MISSING $var"; fi
    done
    if [ -n "$MISSING" ]; then
        echo -e "${RED}✗ --upload needs App Store Connect credentials${NC}"
        echo "  Missing:$MISSING"
        echo ""
        echo "  These are the same values the deploy workflow passes to 'fastlane ios beta'."
        exit 1
    fi
    echo -e "  upload:   ${YELLOW}yes - will replace screenshots on the editable App Store version${NC}"
else
    echo -e "  upload:   ${GREEN}no (pass --upload to publish)${NC}"
fi

echo -e "  output:   ${GREEN}$OUTPUT_DIR${NC}"
echo ""

# --- Capture ----------------------------------------------------------------

cd "$IOS_DIR"

LANE="screenshots"
if [ "$UPLOAD" -eq 1 ]; then
    LANE="screenshots_upload"
fi

echo -e "${BLUE}▶ Running: $FASTLANE_CMD ios $LANE${NC}"
echo -e "${YELLOW}  The flows wait for charts to sync from the CDN, so the first${NC}"
echo -e "${YELLOW}  device takes several minutes before the first capture.${NC}"
echo ""

$FASTLANE_CMD ios "$LANE"

# --- Report -----------------------------------------------------------------

echo ""
if [ -d "$OUTPUT_DIR" ]; then
    COUNT=$(find "$OUTPUT_DIR" -name "*.png" | wc -l | tr -d ' ')
    echo -e "${GREEN}✓ Captured $COUNT screenshot(s)${NC}"
    find "$OUTPUT_DIR" -name "*.png" -exec ls -lh {} \; | awk '{print "   " $9 " (" $5 ")"}'
    if [ "$OPEN_AFTER" -eq 1 ]; then
        open "$OUTPUT_DIR"
    fi
else
    echo -e "${RED}✗ No output directory at $OUTPUT_DIR${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}Done.${NC}"
if [ "$UPLOAD" -eq 0 ]; then
    echo -e "Review them, then publish with: ${YELLOW}./scripts/capture-ios-screenshots.sh --upload${NC}"
fi
