#!/bin/bash

# Provision an Android emulator for clean demo recordings:
#   - fresh boot (optional wipe)
#   - skip setup wizard
#   - black wallpaper + dark theme
#   - hide common launcher apps
#   - build + install fastbreak
#   - save a reusable AVD snapshot
#
# Usage:
#   ./scripts/provision-android-demo-emulator.sh
#   EMULATOR_NAME=Pixel_3a_API_36 WIPE_DATA=0 ./scripts/provision-android-demo-emulator.sh
#   INTERACTIVE_HOME_SETUP=1 ./scripts/provision-android-demo-emulator.sh

set -e

EMULATOR_NAME="${EMULATOR_NAME:-Pixel_9_Pro_API_36}"
SNAPSHOT_NAME="${SNAPSHOT_NAME:-demo-clean}"
WIPE_DATA="${WIPE_DATA:-1}"
SAVE_SNAPSHOT="${SAVE_SNAPSHOT:-1}"
INTERACTIVE_HOME_SETUP="${INTERACTIVE_HOME_SETUP:-0}"
SKIP_BUILD="${SKIP_BUILD:-0}"
PACKAGE_NAME="${PACKAGE_NAME:-com.joebad.fastbreak}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TMP_DIR="${TMPDIR:-/tmp}/fastbreak-emulator-provision"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${GREEN}$1${NC}"; }
warn() { echo -e "${YELLOW}$1${NC}"; }
err() { echo -e "${RED}$1${NC}" >&2; }

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || {
        err "✗ Required command not found: $1"
        exit 1
    }
}

snapshot_exists() {
    local avd_name="$1"
    local snapshot_name="$2"
    local avd_home
    for avd_home in "${ANDROID_AVD_HOME:-}" "$HOME/.android/avd" "$HOME/Library/Android/avd"; do
        [ -n "$avd_home" ] || continue
        if [ -d "$avd_home/${avd_name}.avd/snapshots/${snapshot_name}" ]; then
            return 0
        fi
    done
    return 1
}

wait_for_boot() {
    log "  → Waiting for emulator to boot..."
    adb wait-for-device
    local attempts=0
    while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 2
        attempts=$((attempts + 1))
        if [ "$attempts" -gt 90 ]; then
            err "✗ Emulator boot timed out"
            exit 1
        fi
    done
    sleep 5
}

start_emulator() {
    if ! emulator -list-avds 2>/dev/null | grep -q "^${EMULATOR_NAME}$"; then
        err "✗ Emulator '$EMULATOR_NAME' not found"
        echo ""
        warn "Available emulators:"
        emulator -list-avds | sed 's/^/  /'
        exit 1
    fi

    local emulator_args=(-avd "$EMULATOR_NAME" -no-snapshot-load -dns-server 8.8.8.8,8.8.4.4)
    if [ "$WIPE_DATA" = "1" ]; then
        emulator_args+=(-wipe-data)
        log "  → Starting $EMULATOR_NAME with a fresh data partition..."
    else
        log "  → Starting $EMULATOR_NAME (keeping existing data)..."
    fi

    mkdir -p "$TMP_DIR"
    emulator "${emulator_args[@]}" > "$TMP_DIR/emulator.log" 2>&1 &
    echo "  → Emulator log: $TMP_DIR/emulator.log"
    wait_for_boot
}

skip_setup_wizard() {
    log "Skipping setup wizard..."
    adb shell settings put global device_provisioned 1 >/dev/null 2>&1 || true
    adb shell settings put secure user_setup_complete 1 >/dev/null 2>&1 || true
    adb shell settings put global setup_wizard_has_run 1 >/dev/null 2>&1 || true
    adb shell pm disable-user --user 0 com.google.android.setupwizard >/dev/null 2>&1 || true
    adb shell pm disable-user --user 0 com.android.provision >/dev/null 2>&1 || true
    adb shell am force-stop com.google.android.setupwizard >/dev/null 2>&1 || true
    adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}

enable_dark_theme() {
    log "Enabling dark theme..."
    adb shell cmd uimode night yes >/dev/null 2>&1 || true
    adb shell settings put secure ui_night_mode 2 >/dev/null 2>&1 || true
}

create_black_wallpaper() {
    local out="$1"
    local width="$2"
    local height="$3"

    if command -v ffmpeg >/dev/null 2>&1; then
        ffmpeg -y -f lavfi -i "color=c=black:s=${width}x${height}" -frames:v 1 "$out" >/dev/null 2>&1
        return 0
    fi

    if command -v python3 >/dev/null 2>&1; then
        python3 - "$out" "$width" "$height" <<'PY'
import sys
out, w, h = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
try:
    from PIL import Image
    Image.new("RGB", (w, h), (0, 0, 0)).save(out)
except ImportError:
    # 1x1 black PNG fallback; Android scales wallpaper
    import base64
    png = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAD0lEQVQ42mNk+P+/HgAFhAJ/wlseKgAAAABJRU5ErkJggg=="
    )
    with open(out, "wb") as f:
        f.write(png)
PY
        return 0
    fi

    err "✗ Need ffmpeg or python3 to create a black wallpaper"
    exit 1
}

set_black_wallpaper() {
    log "Setting black wallpaper..."
    local size
    size="$(adb shell wm size 2>/dev/null | tr -d '\r' | awk '/Physical size/ {print $3}')"
    local width="${WALLPAPER_WIDTH:-1280}"
    local height="${WALLPAPER_HEIGHT:-2856}"
    if [[ "$size" == *x* ]]; then
        width="${size%x*}"
        height="${size#*x}"
    fi

    mkdir -p "$TMP_DIR"
    local wallpaper="$TMP_DIR/black-wallpaper.png"
    create_black_wallpaper "$wallpaper" "$width" "$height"
    adb push "$wallpaper" /sdcard/black-wallpaper.png >/dev/null
    adb shell cmd wallpaper set-system-image file:///sdcard/black-wallpaper.png >/dev/null 2>&1 \
        || adb shell cmd wallpaper set-system-uri file:///sdcard/black-wallpaper.png >/dev/null 2>&1 \
        || warn "⚠ Could not set wallpaper automatically; set a black wallpaper manually in Settings"
}

hide_launcher_apps() {
    log "Hiding common preinstalled apps from the launcher..."
    local packages=(
        com.google.android.youtube
        com.google.android.gm
        com.google.android.apps.maps
        com.android.chrome
        com.google.android.apps.photos
        com.google.android.calendar
        com.google.android.deskclock
        com.android.vending
        com.google.android.googlequicksearchbox
        com.google.android.apps.youtube.music
        com.google.android.apps.docs
        com.google.android.apps.drive
        com.google.android.apps.tachyon
        com.google.android.videos
        com.google.android.contacts
        com.google.android.apps.messaging
        com.google.android.apps.wellbeing
        com.google.android.apps.safetyhub
        com.google.android.apps.walletnfcrel
        com.google.android.apps.subscriptions.red
        com.google.android.apps.bard
        com.google.android.apps.nbu.files
        com.google.android.apps.adm
        com.google.android.apps.chromecast.app
        com.google.android.apps.googleassistant
        com.google.android.gm
        com.android.stk
        com.google.android.apps.photosgo
        com.google.android.apps.magazines
        com.google.android.apps.fitness
        com.google.android.apps.podcasts
        com.google.android.apps.accessibility.voiceaccess
    )

    local pkg
    for pkg in "${packages[@]}"; do
        adb shell pm disable-user --user 0 "$pkg" >/dev/null 2>&1 || true
    done

    # Refresh Pixel Launcher shortcuts after disabling packages.
    adb shell pm clear com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
    adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
    sleep 2
}

build_and_install_app() {
    if [ "$SKIP_BUILD" = "1" ]; then
        log "Skipping build (SKIP_BUILD=1)..."
    else
        log "Building debug APK..."
        cd "$PROJECT_DIR"
        ./gradlew :androidApp:assembleDebug >/dev/null
    fi

    local apk_path
    apk_path="$(find "$PROJECT_DIR/androidApp/build/outputs/apk/debug" -name "*.apk" ! -name "*-androidTest.apk" | head -1)"
    if [ -z "$apk_path" ]; then
        err "✗ Could not find androidApp debug APK"
        exit 1
    fi

    log "Installing fastbreak..."
    adb install -r "$apk_path" >/dev/null
}

launch_fastbreak() {
    log "Launching fastbreak once..."
    adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
    sleep 2
    adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
    sleep 1
}

sync_chart_registry() {
    log "Syncing chart registry (required before saving demo snapshot)..."

    if [ "$SKIP_BUILD" != "1" ]; then
        log "Building androidTest APK for sync helper..."
        cd "$PROJECT_DIR"
        ./gradlew :androidApp:assembleDebugAndroidTest >/dev/null
    fi

    local test_apk_path
    test_apk_path="$(find "$PROJECT_DIR/androidApp/build/outputs/apk/androidTest/debug" -name "*-androidTest.apk" | head -1)"
    if [ -z "$test_apk_path" ]; then
        err "✗ Could not find androidTest APK for chart sync"
        exit 1
    fi

    adb install -r "$test_apk_path" >/dev/null

    adb shell am instrument -w -r \
        -e class "com.joebad.fastbreak.DemoUITests#testHelper_SyncChartsForDemo" \
        com.joebad.fastbreak.test/androidx.test.runner.AndroidJUnitRunner \
        > "$TMP_DIR/sync-output.log" 2>&1 || true

    if ! grep -q "OK (1 test)" "$TMP_DIR/sync-output.log"; then
        err "✗ Chart sync helper failed"
        tail -30 "$TMP_DIR/sync-output.log"
        exit 1
    fi

    if ! grep -q "CHARTS_SYNCED" "$TMP_DIR/sync-output.log" \
        && ! adb logcat -d -s DemoUITest:I 2>/dev/null | grep -q "CHARTS_SYNCED"; then
        warn "⚠ Sync helper passed but CHARTS_SYNCED marker was not found in logs"
    else
        log "✓ Chart registry synced"
    fi

    adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
    sleep 1
}

interactive_home_setup() {
    if [ "$INTERACTIVE_HOME_SETUP" != "1" ]; then
        warn "💡 Tip: if the home screen still has extra icons, rerun with INTERACTIVE_HOME_SETUP=1"
        warn "   to clean it up manually before the snapshot is saved."
        return
    fi

    echo ""
    warn "Manual home-screen cleanup:"
    echo "  1. Long-press and remove any remaining home-screen shortcuts/folders"
    echo "  2. Open the app drawer and drag fastbreak to the home screen if needed"
    echo "  3. Press Enter here when the home screen looks clean"
    read -r _
}

save_snapshot() {
    if [ "$SAVE_SNAPSHOT" != "1" ]; then
        warn "Skipping snapshot save (SAVE_SNAPSHOT=0)"
        return
    fi

    log "Saving AVD snapshot '$SNAPSHOT_NAME'..."
    if adb emu avd snapshot save "$SNAPSHOT_NAME" 2>/dev/null | grep -q OK; then
        if snapshot_exists "$EMULATOR_NAME" "$SNAPSHOT_NAME"; then
            log "✓ Snapshot saved"
        else
            warn "⚠ Snapshot command succeeded, but no snapshot folder was created."
            warn "   This AVD may not have snapshot storage enabled; recordings will reuse emulator data instead."
        fi
    else
        warn "⚠ Could not save snapshot"
    fi
}

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Provision Android Demo Emulator      ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

require_cmd adb
require_cmd emulator

DEVICE_ID="$(adb devices | grep -E 'emulator-[0-9]+' | awk '{print $1}' | head -1)"
if [ -n "$DEVICE_ID" ]; then
    warn "Using already-running emulator: $DEVICE_ID"
    if [ "$WIPE_DATA" = "1" ]; then
        warn "WIPE_DATA=1 ignored because an emulator is already running."
        warn "Stop it first if you want a completely fresh provision."
    fi
else
    start_emulator
    DEVICE_ID="$(adb devices | grep -E 'emulator-[0-9]+' | awk '{print $1}' | head -1)"
fi

if [ -z "$DEVICE_ID" ]; then
    err "✗ No emulator device found"
    exit 1
fi

log "Provisioning $DEVICE_ID ..."
skip_setup_wizard
enable_dark_theme
set_black_wallpaper
hide_launcher_apps
build_and_install_app
sync_chart_registry
interactive_home_setup
save_snapshot

echo ""
echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║         Provisioning Complete         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""
log "Emulator:  $EMULATOR_NAME ($DEVICE_ID)"
log "Snapshot:  $SNAPSHOT_NAME"
echo ""
warn "Next time, boot the demo emulator with:"
echo -e "  ${BLUE}emulator -avd $EMULATOR_NAME -snapshot $SNAPSHOT_NAME${NC}"
echo ""
warn "Or record a demo (uses snapshot automatically when available):"
echo -e "  ${BLUE}cd kmp && ./scripts/record-android-demo.sh${NC}"
echo ""
