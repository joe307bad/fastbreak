#!/bin/bash

# Android Demo Recording Script using UI Automator
# This script runs UI Automator demo tests while recording the emulator screen,
# then converts the recording to an optimized GIF

set -e

# Configuration
EMULATOR_NAME="${EMULATOR_NAME:-Pixel_9_Pro_API_36}"
OUTPUT_DIR="${OUTPUT_DIR:-$(dirname "$0")/../screenshots/demos}"
TEST_CLASS="${TEST_CLASS:-com.joebad.fastbreak.DemoUITests}"
TEST_METHOD="${TEST_METHOD:-testDemo_PinchToZoomAndPan}"
FPS="${FPS:-10}"
SCALE="${SCALE:-600}"
RECORD_DELAY="${RECORD_DELAY:-8}"  # Seconds to wait after test starts before recording
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE_NAME="com.joebad.fastbreak"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║ Android Demo Recording (UI Automator) ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Check dependencies
echo -e "${GREEN}Checking dependencies...${NC}"
command -v adb >/dev/null 2>&1 || { echo -e "${RED}✗ Error: adb not found. Please install Android SDK Platform Tools.${NC}" >&2; exit 1; }
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

# Check for running emulator or start one
echo -e "${GREEN}Checking for Android emulator...${NC}"
DEVICE_ID=$(adb devices | grep -E "emulator-[0-9]+" | awk '{print $1}' | head -1)

if [ -z "$DEVICE_ID" ]; then
    echo -e "${YELLOW}No running emulator found. Starting emulator...${NC}"

    # Check if emulator exists
    if ! emulator -list-avds 2>/dev/null | grep -q "^${EMULATOR_NAME}$"; then
        echo -e "${RED}✗ Emulator '$EMULATOR_NAME' not found${NC}"
        echo ""
        echo -e "${YELLOW}Available emulators:${NC}"
        emulator -list-avds | sed 's/^/  /'
        echo ""
        echo -e "${YELLOW}To use a different emulator, run:${NC}"
        echo -e "  ${BLUE}EMULATOR_NAME=\"Pixel_5_API_34\" $0${NC}"
        echo ""
        exit 1
    fi

    # Start emulator in background
    echo -e "  → Starting $EMULATOR_NAME..."
    emulator -avd "$EMULATOR_NAME" -no-snapshot-load -wipe-data > /dev/null 2>&1 &
    EMULATOR_PID=$!

    # Wait for emulator to boot
    echo -e "  → Waiting for emulator to boot..."
    adb wait-for-device

    # Wait for boot to complete
    while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 1
    done

    # Give UI some time to settle
    sleep 5

    DEVICE_ID=$(adb devices | grep -E "emulator-[0-9]+" | awk '{print $1}' | head -1)
    echo -e "${GREEN}✓ Emulator started: $DEVICE_ID${NC}"
else
    echo -e "${GREEN}✓ Using running emulator: $DEVICE_ID${NC}"
fi
echo ""

# Extract demo name from test method
DEMO_NAME=$(echo "$TEST_METHOD" | sed 's/testDemo_//' | sed 's/\([A-Z]\)/-\1/g' | tr '[:upper:]' '[:lower:]' | sed 's/^-//')
VIDEO_FILE="$OUTPUT_DIR/${DEMO_NAME}.mp4"

# Build the app and test APK
echo -e "${GREEN}Building app and test APK...${NC}"
cd "$PROJECT_DIR"

BUILD_OUTPUT=$(./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest 2>&1)
if echo "$BUILD_OUTPUT" | grep -q "BUILD SUCCESSFUL"; then
    echo -e "${GREEN}✓ Build successful${NC}"
elif echo "$BUILD_OUTPUT" | grep -q "BUILD FAILED"; then
    echo -e "${RED}✗ Build failed${NC}"
    echo "$BUILD_OUTPUT" | grep -E "error:|FAILURE:" | head -10
    exit 1
fi

echo ""

# Install the app and test APK
echo -e "${GREEN}Installing app and test APK...${NC}"
APK_PATH=$(find "$PROJECT_DIR/androidApp/build/outputs/apk/debug" -name "*.apk" ! -name "*-androidTest.apk" | head -1)
TEST_APK_PATH=$(find "$PROJECT_DIR/androidApp/build/outputs/apk/androidTest/debug" -name "*-androidTest.apk" | head -1)

if [ -z "$APK_PATH" ] || [ -z "$TEST_APK_PATH" ]; then
    echo -e "${RED}✗ Could not find APK files${NC}"
    exit 1
fi

adb -s "$DEVICE_ID" install -r "$APK_PATH" 2>&1 | tail -1
adb -s "$DEVICE_ID" install -r "$TEST_APK_PATH" 2>&1 | tail -1
echo -e "${GREEN}✓ App and test APK installed${NC}"
echo ""

# Run the UI Automator test in background
echo -e "${GREEN}Starting UI Automator test...${NC}"
adb -s "$DEVICE_ID" shell am instrument -w -r \
    -e class "${TEST_CLASS}#${TEST_METHOD}" \
    com.joebad.fastbreak.test/androidx.test.runner.AndroidJUnitRunner \
    > "$OUTPUT_DIR/test-output.log" 2>&1 &
TEST_PID=$!
echo -e "${GREEN}✓ Test started (PID: $TEST_PID)${NC}"

# Wait for the "RECORDING_READY" marker in the test output, or fallback to delay
echo -e "  → Waiting for chart to load (max ${RECORD_DELAY}s)..."
WAIT_COUNT=0
while [ $WAIT_COUNT -lt $RECORD_DELAY ]; do
    if grep -q "RECORDING_READY" "$OUTPUT_DIR/test-output.log" 2>/dev/null; then
        echo -e "${GREEN}  ✓ Chart ready signal detected${NC}"
        sleep 1  # Small additional delay to ensure screen is stable
        break
    fi
    sleep 1
    WAIT_COUNT=$((WAIT_COUNT + 1))
done

if [ $WAIT_COUNT -ge $RECORD_DELAY ]; then
    echo -e "${YELLOW}  ⚠ Timeout waiting for ready signal, starting recording anyway${NC}"
fi

# Start screen recording (without --bugreport to remove FPS stats overlay)
echo -e "${GREEN}Starting screen recording...${NC}"
adb -s "$DEVICE_ID" shell screenrecord --bit-rate 8000000 /sdcard/demo-recording.mp4 &
RECORD_PID=$!
echo -e "${GREEN}✓ Recording started (PID: $RECORD_PID)${NC}"
echo ""

# Wait for the test to complete
echo -e "  → Waiting for test to complete...${NC}"
wait $TEST_PID 2>/dev/null || true

# Stop recording (screenrecord stops when Ctrl+C is sent)
echo -e "${GREEN}Stopping recording...${NC}"
sleep 2
adb -s "$DEVICE_ID" shell "pkill -INT screenrecord" 2>/dev/null || true
sleep 2
echo -e "${GREEN}✓ Recording stopped${NC}"
echo ""

# Pull the recording from device
echo -e "${GREEN}Pulling recording from device...${NC}"
adb -s "$DEVICE_ID" pull /sdcard/demo-recording.mp4 "$VIDEO_FILE" 2>&1 | tail -1
adb -s "$DEVICE_ID" shell rm /sdcard/demo-recording.mp4
echo -e "${GREEN}✓ Recording pulled${NC}"
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

# Auto-crop to remove black bars and generate palette for better quality
echo -e "  → Auto-detecting crop area and generating color palette..."
CROP_DETECT=$(ffmpeg -i "$VIDEO_FILE" -vf "cropdetect=24:16:0" -f null - 2>&1 | grep -o "crop=[0-9:]*" | tail -1)
if [ -z "$CROP_DETECT" ]; then
    CROP_DETECT="crop=in_w:in_h"  # Fallback to no crop if detection fails
    echo -e "  ${YELLOW}⚠ Auto-crop detection failed, using full frame${NC}"
else
    echo -e "  ${GREEN}✓ Detected crop: $CROP_DETECT${NC}"
fi

ffmpeg -i "$VIDEO_FILE" -vf "$CROP_DETECT,fps=$FPS,scale=$SCALE:-1:flags=lanczos,palettegen=stats_mode=diff:max_colors=256" -y "$PALETTE_FILE" 2>&1 | grep -v "frame=" || true

# Generate GIF using palette with improved quality settings
echo -e "  → Creating GIF with improved quality..."
ffmpeg -i "$VIDEO_FILE" -i "$PALETTE_FILE" \
    -filter_complex "$CROP_DETECT,fps=$FPS,scale=$SCALE:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle" \
    -y "$GIF_FILE" 2>&1 | grep -v "frame=" || true

# Optimize GIF with better quality settings
echo -e "  → Optimizing GIF..."
gifsicle -O3 --lossy=60 --colors 256 "$GIF_FILE" -o "$GIF_OPTIMIZED"

# Clean up
rm -f "$PALETTE_FILE" "$GIF_FILE"
echo -e "${GREEN}✓ GIF created and optimized${NC}"
echo ""

# Get file sizes
VIDEO_SIZE=$(du -h "$VIDEO_FILE" | cut -f1)
GIF_SIZE=$(du -h "$GIF_OPTIMIZED" | cut -f1)

# Check test results
if grep -q "OK (" "$OUTPUT_DIR/test-output.log"; then
    TEST_STATUS="${GREEN}✓ Test passed${NC}"
elif grep -q "FAILURES" "$OUTPUT_DIR/test-output.log"; then
    TEST_STATUS="${YELLOW}⚠ Test had failures${NC}"
else
    TEST_STATUS="${YELLOW}⚠ Test status unknown${NC}"
fi

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║       Demo Recording Complete!        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""
echo -e "${TEST_STATUS}"
echo ""
echo -e "${GREEN}📹 Video: ${VIDEO_FILE}${NC}"
echo -e "   Size: $VIDEO_SIZE"
echo ""
echo -e "${GREEN}🎬 GIF:   ${GIF_OPTIMIZED}${NC}"
echo -e "   Size: $GIF_SIZE"
echo ""
echo -e "${YELLOW}💡 Tips:${NC}"
echo -e "  • Edit demos in: androidApp/src/androidTest/kotlin/com/joebad/fastbreak/DemoUITests.kt"
echo -e "  • Run specific test: TEST_METHOD=testDemo_PinchToZoomAndPan ./scripts/record-android-demo.sh"
echo -e "  • Change quality: FPS=15 SCALE=800 ./scripts/record-android-demo.sh"
echo -e "  • Use different emulator: EMULATOR_NAME=\"Pixel_5_API_34\" ./scripts/record-android-demo.sh"
echo -e "  • Delay recording start: RECORD_DELAY=5 ./scripts/record-android-demo.sh"
echo ""
