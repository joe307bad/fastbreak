# Setup Guide - iOS Demo Recording

## One-Time Setup (5 minutes)

Before you can record demos, you need to add a UI Test target to your Xcode project.

### Step 1: Open Project in Xcode

```bash
cd /Users/joebad/Source/fastbreak/kmp
open iosApp/iosApp.xcworkspace
```

### Step 2: Add UI Testing Bundle

1. In Xcode, click on the **iosApp project** (blue icon) in the left sidebar
2. At the bottom of the target list, click the **+** button
3. Select **iOS → UI Testing Bundle**
4. Click **Next**
5. **Product Name**: Enter `iosAppUITests`
6. **Team**: Select your team (or leave as None for local testing)
7. **Target to be Tested**: Select `iosApp`
8. Click **Finish**

### Step 3: Add DemoUITests.swift to the Target

1. In the left sidebar, find the new **fastbreakUITests** folder (yellow icon)
2. Right-click on **fastbreakUITests** folder
3. Select **Add Files to "fastbreakUITests"...**
4. Navigate to: `scripts/DemoUITests.swift`
5. **IMPORTANT**: Uncheck "Copy items if needed" (we want to reference the original file)
6. Make sure **Target Membership** shows `fastbreakUITests` is checked
7. Click **Add**

### Step 4: Delete the Default Test File (Optional)

1. Find `fastbreakUITests.swift` or `fastbreakUITestsLaunchTests.swift` in the fastbreakUITests folder
2. Right-click → **Delete**
3. Choose **Move to Trash**

### Step 5: Verify Setup

Run this command to verify the test target exists:

```bash
xcodebuild test \
    -workspace iosApp/iosApp.xcworkspace \
    -scheme Fastbreak \
    -destination "platform=iOS Simulator,name=iPhone 16" \
    -only-testing:"fastbreakUITests/DemoUITests/testDemo_PinchToZoomAndPan" \
    -dry-run
```

If you see "Build settings from command line" and no errors, you're good to go!

### Step 6: Run Your First Demo

```bash
./scripts/record-ios-demo.sh
```

## Troubleshooting Setup

### "Testing target not found"

Make sure you:
- Named the target exactly `fastbreakUITests`
- Added `DemoUITests.swift` to the target
- Selected `iosApp` as the "Target to be Tested"

### "No such module 'XCTest'"

This usually means the file wasn't added to the UI Test target correctly. Try:
1. Select `DemoUITests.swift` in Xcode
2. Open the **File Inspector** (right sidebar)
3. Under **Target Membership**, make sure only `fastbreakUITests` is checked

### Still not working?

Try rebuilding the project:

```bash
cd /Users/joebad/Source/fastbreak/kmp
xcodebuild clean \
    -workspace iosApp/iosApp.xcworkspace \
    -scheme Fastbreak
```

Then try recording again.

## What Gets Created

After setup, your project structure will look like:

```
iosApp.xcworkspace
├── iosApp/
│   ├── (your app code)
│   └── ...
└── fastbreakUITests/       ← New UI test target
    └── DemoUITests.swift   ← Link to scripts/DemoUITests.swift
```

## Next Steps

Once setup is complete, you can:

1. **Record demos**: `./scripts/record-ios-demo.sh`
2. **List available demos**: `./scripts/demo-recorder.sh list`
3. **Interactive mode**: `./scripts/demo-recorder.sh`
4. **Customize demos**: Edit `scripts/DemoUITests.swift`

See [README.md](README.md) for full documentation.
