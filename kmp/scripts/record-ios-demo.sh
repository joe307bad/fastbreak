#!/bin/bash

# iOS Demo Recording Script using XCUITest
# This script runs XCUITest demo walkthroughs while recording the simulator screen,
# then converts the recording to an optimized GIF

set -e

# Configuration
SIMULATOR_NAME="${SIMULATOR_NAME:-iPhone 16}"
OUTPUT_DIR="${OUTPUT_DIR:-$(dirname "$0")/../screenshots/demos}"
TEST_NAME="${TEST_NAME:-testDemo_PinchToZoomAndPan}"
FPS="${FPS:-10}"
SCALE="${SCALE:-600}"
RECORD_DELAY="${RECORD_DELAY:-0}"  # Seconds to wait after test starts before recording (to skip app launch)
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  iOS Demo Recording Script (XCUITest) ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Check dependencies
echo -e "${GREEN}Checking dependencies...${NC}"
command -v xcrun >/dev/null 2>&1 || { echo -e "${RED}✗ Error: xcrun not found. Please install Xcode.${NC}" >&2; exit 1; }
command -v ffmpeg >/dev/null 2>&1 || {
    echo -e "${YELLOW}⚠ ffmpeg not found. Installing via Homebrew...${NC}"
    brew install ffmpeg
}
command -v gifsicle >/dev/null 2>&1 || {
    echo -e "${YELLOW}⚠ gifsicle not found. Installing via Homebrew...${NC}"
    brew install gifsicle
}
echo -e "${GREEN}✓ All dependencies installed${NC}"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Find simulator
echo -e "${GREEN}Finding iOS simulator...${NC}"
SIMULATOR_UDID=$(xcrun simctl list devices available | grep "$SIMULATOR_NAME" | grep -v "unavailable" | head -1 | grep -o -E '[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}')

if [ -z "$SIMULATOR_UDID" ]; then
    echo -e "${RED}✗ Simulator '$SIMULATOR_NAME' not found${NC}"
    echo ""
    echo -e "${YELLOW}Available iPhone simulators:${NC}"
    xcrun simctl list devices available | grep "iPhone" | sed 's/^/  /'
    echo ""
    echo -e "${YELLOW}To use a different simulator, run:${NC}"
    echo -e "  ${BLUE}SIMULATOR_NAME=\"iPhone 16 Pro\" $0${NC}"
    echo ""
    exit 1
fi

echo -e "${GREEN}✓ Using simulator: $SIMULATOR_NAME${NC}"
echo -e "  UDID: $SIMULATOR_UDID"
echo ""

# Boot simulator if not already booted
echo -e "${GREEN}Booting simulator...${NC}"
if xcrun simctl list devices | grep "$SIMULATOR_UDID" | grep -q "Booted"; then
    echo -e "${GREEN}✓ Simulator already booted${NC}"
else
    xcrun simctl boot "$SIMULATOR_UDID"
    echo -e "${GREEN}✓ Waiting for simulator to boot...${NC}"
    xcrun simctl bootstatus "$SIMULATOR_UDID" -b
fi

# Open Simulator.app GUI (needed for proper recording)
echo -e "${GREEN}Opening Simulator.app...${NC}"
open -a Simulator
sleep 2  # Give Simulator.app time to open
echo -e "${GREEN}✓ Simulator.app opened${NC}"
echo ""

# Extract demo name from test name
DEMO_NAME=$(echo "$TEST_NAME" | sed 's/testDemo_//' | sed 's/\([A-Z]\)/-\1/g' | tr '[:upper:]' '[:lower:]' | sed 's/^-//')
VIDEO_FILE="$OUTPUT_DIR/${DEMO_NAME}.mov"

# Run XCUITest (this will build, install, and launch the app)
echo -e "${GREEN}Running XCUITest demo: ${TEST_NAME}...${NC}"
cd "$PROJECT_DIR"

# Build the app first
echo -e "  → Building app..."
xcodebuild build \
    -workspace iosApp/iosApp.xcworkspace \
    -scheme Fastbreak \
    -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
    -configuration Debug \
    2>&1 | grep -E "Build succeeded|Build failed|error:" || true

# Install the app (but don't launch yet - let the test launch it while recording)
echo -e "  → Installing app to simulator..."
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData/iosApp*/Build/Products/Debug-iphonesimulator -name "*.app" -type d 2>/dev/null | grep -v "Runner" | grep -v "UITests" | head -1)
if [ -n "$APP_PATH" ]; then
    xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
    echo -e "${GREEN}✓ App installed${NC}"

    # Get bundle ID for later
    BUNDLE_ID=$(defaults read "$APP_PATH/Info.plist" CFBundleIdentifier 2>/dev/null || echo "com.joebad.fastbreak")
else
    echo -e "${RED}✗ Could not find built app${NC}"
    exit 1
fi
echo ""

# Run the UI test with parallel testing DISABLED (prevents cloning)
# We'll run this in background so we can start recording after app launches
echo -e "  → Starting UI test in background...${NC}"
xcodebuild build-for-testing test-without-building \
    -workspace iosApp/iosApp.xcworkspace \
    -scheme Fastbreak \
    -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
    -only-testing:"fastbreakUITests/DemoUITests/${TEST_NAME}" \
    -parallel-testing-enabled NO \
    > "$OUTPUT_DIR/test-output.log" 2>&1 &
TEST_PID=$!
echo -e "${GREEN}✓ Test started (PID: $TEST_PID)${NC}"

# Wait for the app to appear in the process list (meaning it has launched)
echo -e "  → Waiting for app to launch..."
WAIT_COUNT=0
while [ $WAIT_COUNT -lt 30 ]; do
    if xcrun simctl spawn "$SIMULATOR_UDID" launchctl list | grep -q "$BUNDLE_ID"; then
        echo -e "${GREEN}✓ App detected, waiting ${RECORD_DELAY}s before recording...${NC}"
        sleep $RECORD_DELAY
        break
    fi
    sleep 0.5
    WAIT_COUNT=$((WAIT_COUNT + 1))
done

if [ $WAIT_COUNT -ge 30 ]; then
    echo -e "${YELLOW}⚠ App launch not detected, starting recording anyway...${NC}"
fi

# Start recording now that app is visible
echo -e "${GREEN}Starting screen recording...${NC}"
xcrun simctl io "$SIMULATOR_UDID" recordVideo --codec=h264 --force "$VIDEO_FILE" &
RECORD_PID=$!
echo -e "${GREEN}✓ Recording started (PID: $RECORD_PID)${NC}"
echo ""

# Wait for the test to complete
echo -e "  → Waiting for test to complete...${NC}"
wait $TEST_PID 2>/dev/null || true


# Check if test passed
if tail -20 "$OUTPUT_DIR/test-output.log" | grep -q "Testing failed"; then
    echo -e "${YELLOW}⚠ Test may have encountered issues, but recording will continue${NC}"
else
    echo -e "${GREEN}✓ Test completed${NC}"
fi
echo ""

# Stop recording
echo -e "${GREEN}Stopping recording...${NC}"
kill -INT $RECORD_PID 2>/dev/null || true
wait $RECORD_PID 2>/dev/null || true
sleep 2
echo -e "${GREEN}✓ Recording stopped${NC}"
echo ""

# Check if video was created
if [ ! -f "$VIDEO_FILE" ]; then
    echo -e "${RED}✗ Error: Video file not created${NC}"
    exit 1
fi

# Convert to GIF
echo -e "${GREEN}Converting to GIF...${NC}"
PALETTE_FILE="$OUTPUT_DIR/palette-${DEMO_NAME}.png"
GIF_FILE="$OUTPUT_DIR/${DEMO_NAME}-temp.gif"
GIF_OPTIMIZED="$OUTPUT_DIR/${DEMO_NAME}.gif"

# Generate palette for better quality
echo -e "  → Generating color palette..."
ffmpeg -i "$VIDEO_FILE" -vf "fps=$FPS,scale=$SCALE:-1:flags=lanczos,palettegen=stats_mode=diff" -y "$PALETTE_FILE" 2>&1 | grep -v "frame=" || true

# Generate GIF using palette
echo -e "  → Creating GIF..."
ffmpeg -i "$VIDEO_FILE" -i "$PALETTE_FILE" \
    -filter_complex "fps=$FPS,scale=$SCALE:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" \
    -y "$GIF_FILE" 2>&1 | grep -v "frame=" || true

# Optimize GIF
echo -e "  → Optimizing GIF..."
gifsicle -O3 --lossy=80 --colors 256 "$GIF_FILE" -o "$GIF_OPTIMIZED"

# Clean up
rm -f "$PALETTE_FILE" "$GIF_FILE"
echo -e "${GREEN}✓ GIF created and optimized${NC}"
echo ""

# Get file sizes
VIDEO_SIZE=$(du -h "$VIDEO_FILE" | cut -f1)
GIF_SIZE=$(du -h "$GIF_OPTIMIZED" | cut -f1)

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║       Demo Recording Complete!        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}📹 Video: ${VIDEO_FILE}${NC}"
echo -e "   Size: $VIDEO_SIZE"
echo ""
echo -e "${GREEN}🎬 GIF:   ${GIF_OPTIMIZED}${NC}"
echo -e "   Size: $GIF_SIZE"
echo ""
echo -e "${YELLOW}💡 Tips:${NC}"
echo -e "  • Edit demos in: scripts/DemoUITests.swift"
echo -e "  • Run specific demo: TEST_NAME=testDemo_PinchToZoomAndPan ./scripts/record-ios-demo.sh"
echo -e "  • Change quality: FPS=15 SCALE=800 ./scripts/record-ios-demo.sh"
echo -e "  • Delay recording start: RECORD_DELAY=3 ./scripts/record-ios-demo.sh"
echo -e "  • List available tests: grep 'func testDemo' scripts/DemoUITests.swift"
echo ""
