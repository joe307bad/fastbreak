# Adding UI Tests to Fastbreak Scheme

The UI test target exists but isn't included in the Fastbreak scheme. Follow these steps:

## Steps

1. **Open the workspace**:
   ```bash
   cd /Users/joebad/Source/fastbreak/kmp
   open iosApp/iosApp.xcworkspace
   ```

2. **Edit the Scheme**:
   - At the top of Xcode, click on **Fastbreak** (next to the stop button)
   - Select **Edit Scheme...** (or press ⌘ + <)

3. **Add UI Tests**:
   - In the left sidebar, click **Test**
   - You should see `iosApp` under "Test"
   - Click the **+** button at the bottom left
   - Find and select **fastbreakUITests**
   - Click **Add**
   - Make sure the checkbox next to `fastbreakUITests` is **checked**

4. **Close**:
   - Click **Close**

5. **Verify**:
   ```bash
   cd /Users/joebad/Source/fastbreak/kmp
   ./scripts/record-ios-demo.sh
   ```

## Alternative: Command Line Method

If you prefer not to use Xcode UI, you can also just run the tests directly without the scheme:

```bash
xcodebuild test \
    -workspace iosApp/iosApp.xcworkspace \
    -scheme Fastbreak \
    -destination "platform=iOS Simulator,name=iPhone 16" \
    -testPlan iosAppUITests
```

But the scheme method is cleaner for future runs.

## Quick Visual Check

After editing the scheme, when you click on the Fastbreak scheme dropdown, you should see a **Test** option that includes `fastbreakUITests`.
