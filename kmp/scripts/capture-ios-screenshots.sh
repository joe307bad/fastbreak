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
MOCK_DIR="$SCRIPT_DIR/wiremock"
MOCK_JAR="$MOCK_DIR/wiremock-standalone.jar"
MOCK_VERSION="3.9.1"
MOCK_PORT="${MOCK_PORT:-8089}"
MOCK_LOG="$MOCK_DIR/wiremock.log"
MOCK_PID=""

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
    # Two ways to authenticate: the App Store Connect API key CI uses, or the
    # Apple ID session fastlane stores after an interactive sign-in. Either is
    # fine; neither is not.
    HAVE_KEY=1
    for var in APP_STORE_CONNECT_API_KEY_KEY_ID APP_STORE_CONNECT_API_KEY_ISSUER_ID APP_STORE_CONNECT_API_KEY_KEY; do
        if [ -z "${!var:-}" ]; then HAVE_KEY=0; fi
    done

    APPLE_ID="$(sed -n 's/^ *apple_id("\([^"]*\)").*/\1/p' "$IOS_DIR/fastlane/Appfile" | head -1)"
    HAVE_SESSION=0
    if [ -n "$APPLE_ID" ] && [ -d "$HOME/.fastlane/spaceship/$APPLE_ID" ]; then
        HAVE_SESSION=1
    fi

    if [ "$HAVE_KEY" -eq 1 ]; then
        echo -e "  auth:     ${GREEN}App Store Connect API key${NC}"
    elif [ "$HAVE_SESSION" -eq 1 ]; then
        echo -e "  auth:     ${YELLOW}stored Apple ID session ($APPLE_ID)${NC}"
        echo -e "            ${YELLOW}Apple may prompt for a 2FA code.${NC}"
    else
        echo -e "${RED}✗ --upload has no way to authenticate${NC}"
        echo ""
        echo "  Either export the three API key variables the deploy workflow uses:"
        echo "    APP_STORE_CONNECT_API_KEY_KEY_ID"
        echo "    APP_STORE_CONNECT_API_KEY_ISSUER_ID"
        echo "    APP_STORE_CONNECT_API_KEY_KEY"
        echo ""
        echo "  or sign in once so fastlane stores a session:"
        echo -e "    ${YELLOW}cd kmp/iosApp && fastlane spaceauth -u $APPLE_ID${NC}"
        exit 1
    fi
    echo -e "  upload:   ${YELLOW}yes - will replace screenshots on the editable App Store version${NC}"
else
    echo -e "  upload:   ${GREEN}no (pass --upload to publish)${NC}"
fi

echo -e "  output:   ${GREEN}$OUTPUT_DIR${NC}"
echo ""

# --- Mock API ---------------------------------------------------------------
#
# Screenshots are taken against a WireMock on this machine, not the production
# CDN: a capture should not change because a score did, and nothing should ever
# photograph a sync banner waiting on the network.

if ! command -v java >/dev/null 2>&1; then
    echo -e "${RED}✗ java not found - WireMock needs a JRE${NC}"
    echo -e "  ${YELLOW}brew install openjdk${NC}"
    exit 1
fi

# Stubs are generated, not committed: they are ~15MB of real chart payloads.
if [ ! -f "$MOCK_DIR/mappings/registry.json" ] || [ "${REFRESH_STUBS:-0}" = "1" ]; then
    echo -e "${BLUE}▶ Building mock API stubs${NC}"
    REFRESH_FLAG=""
    if [ "${REFRESH_STUBS:-0}" = "1" ]; then REFRESH_FLAG="--refresh"; fi
    python3 "$SCRIPT_DIR/seed-wiremock-stubs.py" $REFRESH_FLAG \
        || { echo -e "${RED}✗ Could not build stubs${NC}"; exit 1; }
fi

if [ ! -f "$MOCK_JAR" ]; then
    echo -e "${BLUE}▶ Downloading WireMock $MOCK_VERSION${NC}"
    curl -fsSL -o "$MOCK_JAR" \
        "https://repo1.maven.org/maven2/org/wiremock/wiremock-standalone/${MOCK_VERSION}/wiremock-standalone-${MOCK_VERSION}.jar" \
        || { echo -e "${RED}✗ Could not download WireMock${NC}"; exit 1; }
fi

stop_mock() {
    if [ -n "$MOCK_PID" ] && kill -0 "$MOCK_PID" 2>/dev/null; then
        kill "$MOCK_PID" 2>/dev/null || true
        wait "$MOCK_PID" 2>/dev/null || true
        echo -e "${GREEN}✓ Stopped mock API${NC}"
    fi
}
trap stop_mock EXIT INT TERM

# A mock left running from an earlier attempt would serve stale stubs.
if lsof -ti ":$MOCK_PORT" >/dev/null 2>&1; then
    echo -e "${YELLOW}  Port $MOCK_PORT busy, stopping what is there${NC}"
    lsof -ti ":$MOCK_PORT" | xargs kill 2>/dev/null || true
    sleep 2
fi

echo -e "${BLUE}▶ Starting mock API on port $MOCK_PORT${NC}"
java -jar "$MOCK_JAR" --port "$MOCK_PORT" --root-dir "$MOCK_DIR" --disable-banner \
    > "$MOCK_LOG" 2>&1 &
MOCK_PID=$!

for _ in $(seq 1 30); do
    if curl -sf -m 2 "http://127.0.0.1:$MOCK_PORT/registry" >/dev/null 2>&1; then break; fi
    sleep 1
done
if ! curl -sf -m 2 "http://127.0.0.1:$MOCK_PORT/registry" >/dev/null 2>&1; then
    echo -e "${RED}✗ Mock API never answered on port $MOCK_PORT${NC}"
    tail -20 "$MOCK_LOG"
    exit 1
fi
STUB_COUNT=$(curl -s "http://127.0.0.1:$MOCK_PORT/registry" | tr ',' '\n' | grep -c '"interval"' || true)
echo -e "${GREEN}✓ Mock API serving $STUB_COUNT registry entries${NC}"
echo ""

# --- Capture ----------------------------------------------------------------

cd "$IOS_DIR"

LANE="screenshots"
if [ "$UPLOAD" -eq 1 ]; then
    LANE="screenshots_upload"
fi

echo -e "${BLUE}▶ Running: $FASTLANE_CMD ios $LANE${NC}"
echo -e "${YELLOW}  Two devices, ten screenshots each. Most of the time goes on${NC}"
echo -e "${YELLOW}  building the app once per device.${NC}"
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
