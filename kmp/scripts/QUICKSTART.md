# Quick Start Guide - Automated Demo Recording

Get started recording iOS app demos in 5 minutes!

## Step 1: One-Time Setup

### Add UI Test Target to Xcode

1. Open `iosApp/iosApp.xcworkspace` in Xcode
2. **File → New → Target**
3. Select **iOS → UI Testing Bundle**
4. Name it: `iosAppUITests`
5. Click **Finish**

### Add DemoUITests.swift

1. Right-click `iosAppUITests` folder in Xcode
2. **Add Files to "iosAppUITests"**
3. Navigate to and select `scripts/DemoUITests.swift`
4. Ensure **Copy items if needed** is checked
5. Click **Add**

That's it! Setup complete.

## Step 2: Record Your First Demo

```bash
cd kmp/scripts
./demo-recorder.sh
```

This will:
1. Show you a list of available demos
2. Let you pick one to record
3. Boot the simulator
4. Run the demo
5. Create an optimized GIF

## Step 3: Use the GIFs

Find your GIFs in: `screenshots/demos/`

Add to your docs:

```markdown
![Demo](../kmp/screenshots/demos/pinch-to-zoom-and-pan.gif)
```

## Common Commands

```bash
# Interactive mode (recommended)
./demo-recorder.sh

# List all demos
./demo-recorder.sh list

# Record all demos
./demo-recorder.sh all

# Record specific demo directly
./record-ios-demo.sh  # Runs default demo

# High quality recording
FPS=15 SCALE=800 ./record-ios-demo.sh

# Different simulator
SIMULATOR_NAME="iPhone 14 Pro" ./record-ios-demo.sh
```

## Customizing Demos

Edit `DemoUITests.swift` to add your own demos:

```swift
func testDemo_MyFeature() throws {
    sleep(2)  // Wait for app to load

    // Tap a button
    app.buttons["my_button_id"].tap()
    sleep(1)

    // Swipe a chart
    app.otherElements["chart"].swipeLeft()
    sleep(1)

    sleep(2)  // Final pause
}
```

Run it:

```bash
./demo-recorder.sh list  # Find it in the list
./demo-recorder.sh 5     # Record by number
```

## Adding Accessibility Identifiers

Make your UI testable by adding identifiers in your Compose code:

```kotlin
Button(
    onClick = { /* ... */ },
    modifier = Modifier.testTag("my_button_id")
) {
    Text("Click Me")
}
```

## Troubleshooting

**"Simulator not found"**
```bash
xcrun simctl list devices available | grep iPhone
SIMULATOR_NAME="iPhone 15" ./record-ios-demo.sh
```

**"Element not found in test"**
- Add accessibility identifiers to your UI
- Use Xcode's Accessibility Inspector to find element IDs
- Increase wait timeout in the test

**Need help?**
See the full [README.md](README.md) for detailed documentation.

## Examples

Here are the pre-built demos:

1. **Pinch to Zoom** - Shows pinch-to-zoom and panning on charts
2. **Pinning Teams** - Demonstrates pinning/unpinning teams
3. **Highlighting Data Points** - Shows chart interaction
4. **Complete Walkthrough** - Full app tour

## Tips

- Keep demos under 15 seconds for optimal GIF size
- Add `sleep()` calls between actions for smooth playback
- Test in Xcode first before recording
- Use descriptive test names starting with `testDemo_`

---

That's it! You're ready to create automated demos. 🎬
