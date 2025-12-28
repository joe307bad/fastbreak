#!/bin/bash

# Script to create a UI Testing scheme for demo recording
# This creates a new scheme specifically for running UI tests

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCHEME_NAME="FastbreakUITests"

echo "Creating UI Test Scheme: $SCHEME_NAME"
echo ""

cd "$PROJECT_DIR"

# Check if fastbreakUITests target exists
if ! xcodebuild -workspace iosApp/iosApp.xcworkspace -list | grep -q "fastbreakUITests"; then
    echo "Error: fastbreakUITests target not found"
    echo "Please create the UI test target first (see SETUP.md)"
    exit 1
fi

# Create the scheme using xcodebuild
echo "Creating scheme..."
xcodebuild -workspace iosApp/iosApp.xcworkspace \
    -scheme Fastbreak \
    -showBuildSettings > /dev/null 2>&1

# The scheme will be created in the xcuserdata directory
SCHEME_DIR="$PROJECT_DIR/iosApp/iosApp.xcodeproj/xcshareddata/xcschemes"
mkdir -p "$SCHEME_DIR"

# Create a new scheme file
cat > "$SCHEME_DIR/$SCHEME_NAME.xcscheme" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<Scheme
   LastUpgradeVersion = "1540"
   version = "1.7">
   <BuildAction
      parallelizeBuildables = "YES"
      buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry
            buildForTesting = "YES"
            buildForRunning = "YES"
            buildForProfiling = "YES"
            buildForArchiving = "YES"
            buildForAnalyzing = "YES">
            <BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "7555FF7A242A565900829871"
               BuildableName = "iosApp.app"
               BlueprintName = "iosApp"
               ReferencedContainer = "container:iosApp.xcodeproj">
            </BuildableReference>
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      shouldUseLaunchSchemeArgsEnv = "YES">
      <Testables>
         <TestableReference
            skipped = "NO">
            <BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "UITEST_TARGET_ID"
               BuildableName = "fastbreakUITests.xctest"
               BlueprintName = "fastbreakUITests"
               ReferencedContainer = "container:iosApp.xcodeproj">
            </BuildableReference>
         </TestableReference>
      </Testables>
   </TestAction>
   <LaunchAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      launchStyle = "0"
      useCustomWorkingDirectory = "NO"
      ignoresPersistentStateOnLaunch = "NO"
      debugDocumentVersioning = "YES"
      debugServiceExtension = "internal"
      allowLocationSimulation = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         <BuildableReference
            BuildableIdentifier = "primary"
            BlueprintIdentifier = "7555FF7A242A565900829871"
            BuildableName = "iosApp.app"
            BlueprintName = "iosApp"
            ReferencedContainer = "container:iosApp.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </LaunchAction>
</Scheme>
EOF

echo "✓ Scheme created: $SCHEME_NAME"
echo ""
echo "You can now use this scheme for UI testing:"
echo "  SCHEME_NAME=\"$SCHEME_NAME\" ./scripts/record-ios-demo.sh"
echo ""
