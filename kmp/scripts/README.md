# Automated Demo Recording

This directory contains scripts for automatically recording demos of the fastbreak iOS app and converting them to optimized GIFs for documentation.

## Overview

The demo recording system uses **XCUITest** (Apple's official UI testing framework) to programmatically interact with the app while recording the simulator screen. This ensures consistent, reproducible demos.

## Prerequisites

1. **Xcode** - Required for XCUITest and simulator access
2. **Homebrew** - For installing dependencies (optional, script will auto-install)
3. **ffmpeg** - For video conversion (auto-installed if missing)
4. **gifsicle** - For GIF optimization (auto-installed if missing)

## Setup

### 1. Add UI Test Target to Xcode

First, you need to add a UI Test target to your Xcode project:

1. Open `iosApp/iosApp.xcworkspace` in Xcode
2. Go to **File → New → Target**
3. Select **iOS → UI Testing Bundle**
4. Name it `iosAppUITests`
5. Add the `DemoUITests.swift` file to this target:
   - Right-click `iosAppUITests` folder → **Add Files to "iosAppUITests"**
   - Navigate to `scripts/DemoUITests.swift`
   - Click **Add**

### 2. Configure UI Test Identifiers

Update your app's UI elements with accessibility identifiers to make them testable:

```swift
// Example: In your Compose code
Button(
    onClick = { /* ... */ },
    modifier = Modifier.testTag("nfl_tab") // XCUITest can find this
) {
    Text("NFL")
}

// In UIKit:
button.accessibilityIdentifier = "nfl_tab"
```

### 3. Update Test Script

Edit `scripts/DemoUITests.swift` to match your app's actual UI identifiers and navigation flow.

## Usage

### Basic Usage

Record the default demo (complete walkthrough):

```bash
cd kmp
./scripts/record-ios-demo.sh
```

### Record Specific Demo

Run a specific demo test:

```bash
# Pinch to zoom demo
TEST_NAME=testDemo_PinchToZoomAndPan ./scripts/record-ios-demo.sh

# Pinning teams demo
TEST_NAME=testDemo_PinningTeams ./scripts/record-ios-demo.sh

# Highlighting data points demo
TEST_NAME=testDemo_HighlightingDataPoints ./scripts/record-ios-demo.sh
```

### Custom Configuration

Adjust quality and output settings:

```bash
# Higher quality GIF
FPS=15 SCALE=800 ./scripts/record-ios-demo.sh

# Different simulator
SIMULATOR_NAME="iPhone 14 Pro" ./scripts/record-ios-demo.sh

# Custom output directory
OUTPUT_DIR=./my-demos ./scripts/record-ios-demo.sh

# Combine options
FPS=12 SCALE=700 TEST_NAME=testDemo_PinchToZoomAndPan ./scripts/record-ios-demo.sh
```

## Configuration Options

| Variable | Default | Description |
|----------|---------|-------------|
| `SIMULATOR_NAME` | `iPhone 15` | Name of simulator to use |
| `OUTPUT_DIR` | `screenshots/demos` | Output directory for videos/GIFs |
| `TEST_NAME` | `testDemo_CompleteWalkthrough` | XCUITest method to run |
| `FPS` | `10` | Frames per second for GIF |
| `SCALE` | `600` | Width of GIF in pixels (height auto) |

## Output

After running, you'll find:

- **Video**: `screenshots/demos/{demo-name}.mov` - Full quality recording
- **GIF**: `screenshots/demos/{demo-name}.gif` - Optimized web-ready GIF
- **Log**: `screenshots/demos/test-output.log` - Test execution log

## Available Demos

The `DemoUITests.swift` file contains these pre-configured demos:

1. **testDemo_PinchToZoomAndPan** - Demonstrates pinch-to-zoom and panning on charts
2. **testDemo_PinningTeams** - Shows how to pin/unpin teams
3. **testDemo_HighlightingDataPoints** - Highlights data points on charts
4. **testDemo_CompleteWalkthrough** - Full app tour across all tabs

## Creating Custom Demos

Add new demo walkthroughs by editing `scripts/DemoUITests.swift`:

```swift
func testDemo_YourCustomDemo() throws {
    sleep(2) // Wait for app to load

    // Find and interact with UI elements
    let button = app.buttons["your_button_id"]
    if button.waitForExistence(timeout: 5) {
        button.tap()
        sleep(1)
    }

    // Add more interactions
    let chart = app.otherElements["chart_id"]
    chart.swipeLeft()

    sleep(2) // Final pause before ending
}
```

Then run it:

```bash
TEST_NAME=testDemo_YourCustomDemo ./scripts/record-ios-demo.sh
```

## Troubleshooting

### Test fails with "Unable to find element"

- Check that your UI elements have the correct accessibility identifiers
- Use Xcode's Accessibility Inspector to verify identifiers
- Add longer waits: `element.waitForExistence(timeout: 10)`

### Simulator not found

List available simulators:

```bash
xcrun simctl list devices available | grep iPhone
```

Use the exact name:

```bash
SIMULATOR_NAME="iPhone 14 Pro" ./scripts/record-ios-demo.sh
```

### Recording file not created

- Ensure simulator has screen recording permissions
- Try running Xcode once to accept permissions
- Check disk space

### GIF quality issues

Increase FPS and SCALE:

```bash
FPS=15 SCALE=800 ./scripts/record-ios-demo.sh
```

Or edit the video file directly with your preferred tool.

## File Structure

```
kmp/
├── scripts/
│   ├── record-ios-demo.sh      # Main recording script
│   ├── DemoUITests.swift        # XCUITest demo definitions
│   └── README.md                # This file
└── screenshots/
    └── demos/                   # Output directory
        ├── pinch-to-zoom-and-pan.mov
        ├── pinch-to-zoom-and-pan.gif
        ├── complete-walkthrough.mov
        └── complete-walkthrough.gif
```

## Tips

1. **Keep demos short** - 10-15 seconds is ideal for GIFs
2. **Add sleeps** - Give UI time to animate between actions
3. **Use descriptive names** - Name tests clearly: `testDemo_FeatureName`
4. **Test in Xcode first** - Run tests in Xcode to debug before recording
5. **Optimize GIFs** - Lower FPS (8-10) for smaller file sizes

## Integration with Documentation

Add the generated GIFs to your docs:

```markdown
## Manual

### I. Pinch to Zoom, Drag to pan

![Pinch to Zoom Demo](../kmp/screenshots/demos/pinch-to-zoom-and-pan.gif)

<PlatformSupport ios={true} android={true} web={false} />
```

## Advanced Usage

### Batch Recording

Record all demos at once:

```bash
for test in testDemo_PinchToZoomAndPan testDemo_PinningTeams testDemo_HighlightingDataPoints; do
    TEST_NAME=$test ./scripts/record-ios-demo.sh
done
```

### Custom Video Processing

Keep the `.mov` file and process it manually:

```bash
# Extract specific time range
ffmpeg -i demo.mov -ss 00:00:02 -t 00:00:10 -c copy trimmed.mov

# Different GIF settings
ffmpeg -i demo.mov -vf "fps=20,scale=1000:-1" output.gif
```

## License

These scripts are part of the fastbreak project.
