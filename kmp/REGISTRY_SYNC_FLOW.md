# Registry & Chart Sync Flow

## Expected Flow

### 1. Initial App Load

#### Scenario A: Registry exists in cache
- **NO network request for registry**
- Load registry from cache immediately
- Skip to step 4 (compare chart timestamps)

#### Scenario B: No registry in cache
- Show loading component with message: **"Loading registry..."**
- Progress bar indeterminate or at 0%
- Make network request to `/api/v1/registry`
- When registry loads:
  - Progress bar → 100%
  - Show message: **"Registry successfully loaded"** with ✓ green check icon
  - Continue to step 4

### 2. Registry Successfully Loaded
- Progress bar at 100%
- Show: **"Registry successfully loaded"** with ✓ green check icon
- Display for 0.5 seconds
- Then proceed to chart sync

### 3. After Registry Load: Navigate to Chart Comparison

### 4. Compare Chart Timestamps
- For each chart in registry:
  - Check if chart exists in cache
  - If no cache → needs download
  - If cached → compare `lastUpdated` timestamps
  - If registry timestamp > cached timestamp → needs download

### 5. Download Charts That Need Update
- Show progress indicator: **"Syncing charts..."**
- Progress bar shows: `x/y` (e.g., "3/16")
- Display current chart name being downloaded

### 6. As Each Chart Loads
- Increment progress: `current/total`
- Update progress bar percentage
- Show chart name currently downloading

### 7. All Charts Loaded Successfully
- Progress bar → 100%
- Show: **"Charts successfully loaded"** with ✓ green check icon
- Display for 0.5 seconds
- Auto-dismiss

### 8. If Any Charts Fail
- Progress bar → 100%
- Show: **"Sync failed"** with ✗ red X icon
- Display: "X charts failed to sync"
- Display for 0.5 seconds
- Auto-dismiss

## Navigation Behavior

### When Navigating to Home Screen
- **NEVER show loading component** if registry already exists
- **NEVER re-run sync** unless explicitly triggered by refresh button
- Charts should be immediately clickable

### When Navigating Away and Back
- Preserve state
- Do NOT show "Sync completed" message again
- Do NOT re-sync charts
- Charts remain clickable

## Current Bugs

### Bug 1: Progress Bar Not Showing
**Issue**: Progress bar is not visible or not progressing during sync

**Expected**:
- Progress bar should be visible during registry load
- Progress bar should progress during chart sync
- Progress bar should reach 100% on completion

### Bug 2: Loading Component Shows on Every Navigation
**Issue**: When navigating to a chart and back to home, the loading/sync completed component appears again

**Expected**:
- Component should only show during actual sync operations
- Should NOT show when returning to home screen
- `syncProgress` state should be cleared after auto-dismiss

**Root Cause**:
- `syncProgress` state persists when navigating away
- No check to prevent re-showing completed sync on navigation

## State Management Requirements

### RegistryState
```kotlin
data class RegistryState(
    val registry: Registry? = null,
    val isLoading: Boolean = false,        // True while loading registry
    val isSyncing: Boolean = false,        // True while syncing charts
    val syncProgress: SyncProgress? = null, // Null when not syncing
    val error: String? = null
)
```

### SyncProgress
```kotlin
data class SyncProgress(
    val current: Int,      // Current chart being processed
    val total: Int,        // Total charts to sync
    val currentChart: String, // Name of current chart
    val isComplete: Boolean  // True when current >= total
)
```

## Implementation Phases

### Phase 1: Fix Loading Component Persistence Bug ✅ COMPLETED
- [x] Clear `syncProgress` when navigating away from home
- [x] Clear stale completed sync on screen mount
- [x] Ensure `syncProgress` is cleared after auto-dismiss delay (0.5s)
- [x] Fix progress bar to show 100% when complete
- [x] Hide counter when no charts to sync (0/0)
- [x] Only show counter when total > 0

**Changes Made:**
- Reduced auto-dismiss delay from 3 seconds to 0.5 seconds (as per spec)
- Added `clearSyncProgress()` method to `RegistryContainer`
- Added `clearSyncProgress()` callback to `RootComponent`
- Added `onClearSyncProgress` parameter to `HomeScreen`
- Clear completed sync in `LaunchedEffect` on screen mount
- Clear completed sync in `DisposableEffect` when navigating away
- Updated `App.kt` to wire up the new callback
- **Fixed**: Only call `onInitialLoad()` if registry is null - prevents re-loading when navigating back to home screen
- **Fixed**: Progress bar percentage returns 100 when sync is complete
- **Fixed**: Counter only shows when total > 0 (no more "0/0")

### Phase 2: Fix Progress Bar Display
- [ ] Ensure progress bar is visible during all loading states
- [ ] Show progress bar during registry load (if no cache)
- [ ] Show progress bar during chart sync
- [ ] Progress bar reaches 100% on completion

### Phase 3: Implement Cache-First Registry Load
- [ ] Check for cached registry on `loadRegistry()`
- [ ] If cached, load immediately WITHOUT network request
- [ ] Only fetch from network if no cache exists

### Phase 4: Fix Chart Timestamp Comparison
- [ ] Add detailed logging for timestamp comparison
- [ ] Ensure valid ISO-8601 timestamps
- [ ] Test with updated chart timestamps

### Phase 5: Polish Completion Messages
- [ ] Registry load success message (0.5s)
- [ ] Chart sync success message (0.5s)
- [ ] Auto-dismiss after delay
- [ ] Clear state completely after dismiss

## Testing Checklist

- [ ] First load with no cache shows "Loading registry..."
- [ ] First load downloads all charts with progress
- [ ] Subsequent loads use cache, no registry network request
- [ ] Updated chart timestamps trigger re-download
- [ ] Navigation to chart and back shows NO loading component
- [ ] Refresh button triggers full sync with progress
- [ ] Progress bar visible and progresses correctly
- [ ] Completion messages show for 0.5s then auto-dismiss
- [ ] Charts clickable after sync completes
- [ ] Charts remain clickable after navigation
