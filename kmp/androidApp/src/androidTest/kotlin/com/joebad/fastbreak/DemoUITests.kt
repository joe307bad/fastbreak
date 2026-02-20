package com.joebad.fastbreak

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Automator tests for recording automated app demos
 * This test class contains a demo for pinch to zoom and pan on NFL Team Tiers chart
 */
@RunWith(AndroidJUnit4::class)
class DemoUITests {

    private lateinit var device: UiDevice
    private val packageName = "com.joebad.fastbreak"
    private val launchTimeout = 10000L

    @Before
    fun setUp() {
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Start from the home screen
        device.pressHome()

        // Wait for launcher
        val launcherPackage = device.launcherPackageName
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), launchTimeout)

        // Launch the app
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeout)

        // Give app extra time to load
        Thread.sleep(1000)
    }

    /**
     * Demo test: Pinch to zoom and pan on any Efficiency chart
     */
    @Test
    fun testDemo_PinchToZoomAndPan() {
        println("⏱ Waiting for app to load...")
        Thread.sleep(2000)

        // Find and tap on the second chart with "Efficiency" in the title
        println("🔍 Looking for charts with 'Efficiency' in title...")
        device.wait(Until.hasObject(By.textContains("Efficiency")), 10000)
        val efficiencyCharts = device.findObjects(By.textContains("Efficiency"))

        if (efficiencyCharts.size >= 2) {
            println("✓ Found ${efficiencyCharts.size} 'Efficiency' charts, tapping second one...")
            efficiencyCharts[1].click()
            Thread.sleep(1000)
        } else if (efficiencyCharts.isNotEmpty()) {
            println("⚠ Only found ${efficiencyCharts.size} 'Efficiency' chart(s), tapping first one...")
            efficiencyCharts[0].click()
            Thread.sleep(1000)
        } else {
            println("⚠ 'Efficiency' chart not found, continuing anyway...")
            Thread.sleep(2000)
        }

        println("✓ Chart should be visible, starting pan and zoom demo...")

        // Extra pause to ensure chart is fully rendered before starting gestures
        Thread.sleep(2000)
        println("🎬 RECORDING_READY - Chart fully loaded, beginning demo gestures...")

        // Find the chart element using UiObject (older API) for pinch gesture support
        println("🔍 Looking for chart with testTag 'chart'...")
        val chartObject = device.findObject(UiSelector().descriptionContains("chart"))

        val scrollableObject = if (chartObject.exists()) {
            println("✓ Found chart with testTag")
            chartObject
        } else {
            println("⚠ Chart with testTag not found, trying scrollable element...")
            device.findObject(UiSelector().scrollable(true))
        }

        if (scrollableObject.exists()) {
            println("✓ Found scrollable chart element")

            // Initial pause to show the chart
            Thread.sleep(1000)

            // Zoom in - pinch open gesture
            println("  → Zooming in with pinch open gesture...")
            scrollableObject.pinchOut(50, 50)  // percent and speed
            Thread.sleep(2000)

            // Pan left while zoomed
            println("  → Panning left...")
            val bounds = scrollableObject.bounds
            device.swipe(
                (bounds.right - bounds.width() * 0.3).toInt(),
                bounds.centerY(),
                (bounds.left + bounds.width() * 0.3).toInt(),
                bounds.centerY(),
                10
            )
            Thread.sleep(1000)

            // Pan right
            println("  → Panning right...")
            device.swipe(
                (bounds.left + bounds.width() * 0.3).toInt(),
                bounds.centerY(),
                (bounds.right - bounds.width() * 0.3).toInt(),
                bounds.centerY(),
                10
            )
            Thread.sleep(1000)

            // Zoom out - pinch in gesture
            println("  → Zooming out with pinch in gesture...")
            scrollableObject.pinchIn(50, 50)  // percent and speed
            Thread.sleep(2000)

            // Final zoom in
            println("  → Final zoom in...")
            scrollableObject.pinchOut(50, 50)
            Thread.sleep(2000)
        } else {
            println("⚠ Could not find scrollable element, skipping gestures")
        }

        println("✓ Demo complete!")
    }

    /**
     * Demo test: Manage teams and filter chart data
     * Shows: Side menu > Settings > Manage teams > Search > Select team > NHL tab > Filter chart
     */
    @Test
    fun testDemo_ManageTeamsAndFilter() {
        println("🎬 RECORDING_READY - Starting demo...")

        println("⏱ Waiting for app to load...")
        Thread.sleep(1500)

        // Open side menu by clicking the hamburger menu icon (faster - no extra pause)
        println("🔍 Looking for hamburger menu icon...")
        val menuIcon = device.wait(
            Until.findObject(By.desc("Menu")),
            3000
        )

        if (menuIcon != null) {
            println("✓ Found menu icon, clicking...")
            menuIcon.click()
            Thread.sleep(1000)  // Reduced wait time
            println("✓ Side menu should be open")
        } else {
            println("⚠ Menu icon not found, trying click at top left corner...")
            // Click at approximate location of menu icon (top left)
            device.click(60, 138)
            Thread.sleep(1000)
        }

        // Find and tap "Settings" button by text
        println("🔍 Looking for 'settings' button...")
        val settingsButton = device.wait(
            Until.findObject(By.textContains("settings")),
            3000
        )

        if (settingsButton != null) {
            println("✓ Found 'settings' button, tapping...")
            settingsButton.click()
            Thread.sleep(1000)
        } else {
            println("⚠ settings button not found")
        }

        // Find and tap "Manage Teams" option
        println("🔍 Looking for 'Manage Teams' option...")
        val manageTeamsText = device.wait(
            Until.findObject(By.textContains("manage teams")),
            3000
        )

        if (manageTeamsText != null) {
            println("✓ Found 'Manage Teams', tapping...")
            manageTeamsText.click()
            Thread.sleep(800)
        } else {
            println("⚠ Manage Teams not found")
        }

        // Search for Pittsburgh Penguins
        println("🔍 Looking for search field...")
        val searchFieldSelector = UiSelector().className("android.widget.EditText")
        val searchField = device.findObject(searchFieldSelector)

        if (searchField.exists()) {
            println("✓ Found search field, entering 'Pittsburgh Penguins'...")
            searchField.click()
            Thread.sleep(1000)
            searchField.setText("Pittsburgh Penguins")
            Thread.sleep(1000)
        } else {
            println("⚠ Search field not found")
        }

        // Select Pittsburgh Penguins from results (skip the search field which also contains the text)
        println("🔍 Looking for Pittsburgh Penguins in results...")
        Thread.sleep(500) // Wait for results to appear
        val penguinsMatches = device.findObjects(By.textContains("Penguins"))

        // Find the result item (not the search field) - it should be below the search field
        val penguinsResult = penguinsMatches.find { match ->
            // Skip if it's an EditText (the search field)
            match.className != "android.widget.EditText"
        }

        if (penguinsResult != null) {
            println("✓ Found Penguins in results (${penguinsMatches.size} matches, selecting non-input), clicking...")
            penguinsResult.click()
            Thread.sleep(500)
        } else if (penguinsMatches.size > 1) {
            // Fallback: if we couldn't filter by class, just click the second match
            println("✓ Found ${penguinsMatches.size} Penguins matches, selecting second one...")
            penguinsMatches[1].click()
            Thread.sleep(500)
        } else {
            println("⚠ Pittsburgh Penguins not found in results")
        }

        // Close the team selector bottom sheet by swiping down
        println("🔍 Closing team selector bottom sheet with swipe down...")
        val screenHeight = device.displayHeight
        val screenWidth = device.displayWidth
        device.swipe(
            screenWidth / 2,
            screenHeight / 2,
            screenWidth / 2,
            screenHeight - 100,
            10
        )
        Thread.sleep(500)

        // Navigate back from Settings to home screen
        println("🔍 Looking for Settings back button...")
        val settingsBackButton = device.wait(
            Until.findObject(By.desc("Back")),
            3000
        )

        if (settingsBackButton != null) {
            println("✓ Found Settings back button, tapping...")
            settingsBackButton.click()
            Thread.sleep(500)
        } else {
            println("⚠ Settings back button not found, using device back...")
            device.pressBack()
            Thread.sleep(500)
        }

        // Switch to NHL tab
        println("🔍 Looking for NHL tab...")
        val nhlTab = device.wait(
            Until.findObject(By.text("NHL")),
            3000
        )

        if (nhlTab != null) {
            println("✓ Found NHL tab, tapping...")
            nhlTab.click()
            Thread.sleep(1000)
        } else {
            println("⚠ NHL tab not found")
        }

        // Find and tap "Efficiency" chart
        println("🔍 Looking for 'Efficiency' chart...")
        val efficiencyChart = device.wait(
            Until.findObject(By.textContains("Efficiency")),
            5000
        )

        if (efficiencyChart != null) {
            println("✓ Found 'Efficiency' chart, tapping...")
            efficiencyChart.click()
            Thread.sleep(1000)
        } else {
            println("⚠ Efficiency chart not found")
        }

        // Find and tap PIT filter badge
        println("🔍 Looking for 'PIT' filter badge...")
        val pitBadge = device.wait(
            Until.findObject(By.text("PIT")),
            3000
        )

        if (pitBadge != null) {
            println("✓ Found 'PIT' badge, tapping...")
            pitBadge.click()
            Thread.sleep(1000)
        } else {
            println("⚠ PIT badge not found, trying alternative search...")
            // Try to find any clickable element with PIT text
            val pitAlt = device.wait(
                Until.findObject(By.textContains("PIT").clickable(true)),
                2000
            )
            if (pitAlt != null) {
                pitAlt.click()
                Thread.sleep(1000)
            }
        }

        // Final pause to show the filtered chart
        println("✓ Showing filtered chart...")
        Thread.sleep(2000)

        println("✓ Demo complete!")
    }

    /**
     * Demo test: Highlighting data points
     * Shows: Select PHI and LAL badges > Division badge > Southwest Division > Select top 3 players
     */
    @Test
    fun testDemo_HighlightingDataPoints() {
        println("🎬 RECORDING_READY - Starting demo...")

        println("⏱ Waiting for app to load...")
        Thread.sleep(1500)

        // Find and tap "Player Efficiency" chart (already on NBA tab by default)
        println("🔍 Looking for 'Player Efficiency' chart...")
        val playerEfficiency = device.wait(
            Until.findObject(By.textContains("Player Efficiency")),
            5000
        )

        if (playerEfficiency != null) {
            println("✓ Found 'Player Efficiency', tapping...")
            playerEfficiency.click()
            Thread.sleep(1000)
        } else {
            println("⚠ Player Efficiency chart not found")
        }

        // Find and tap PHI badge
        println("🔍 Looking for 'PHI' filter badge...")
        val phiBadge = device.wait(
            Until.findObject(By.text("PHI")),
            3000
        )

        if (phiBadge != null) {
            println("✓ Found 'PHI' badge, tapping...")
            phiBadge.click()
            Thread.sleep(1000)
        } else {
            println("⚠ PHI badge not found")
        }

        // Find and tap LAL badge
        println("🔍 Looking for 'LAL' filter badge...")
        val lalBadge = device.wait(
            Until.findObject(By.text("LAL")),
            3000
        )

        if (lalBadge != null) {
            println("✓ Found 'LAL' badge, tapping...")
            lalBadge.click()
            Thread.sleep(1000)
        } else {
            println("⚠ LAL badge not found")
        }

        // Find and tap Division badge to open division selector
        println("🔍 Looking for 'Division' badge...")
        val divisionBadge = device.wait(
            Until.findObject(By.textContains("Division")),
            3000
        )

        if (divisionBadge != null) {
            println("✓ Found 'Division' badge, tapping...")
            divisionBadge.click()
            Thread.sleep(1000)
        } else {
            println("⚠ Division badge not found")
        }

        // Select Southwest Division from the list
        println("🔍 Looking for 'Southwest' division...")
        val southwestDivision = device.wait(
            Until.findObject(By.textContains("Southwest")),
            3000
        )

        if (southwestDivision != null) {
            println("✓ Found 'Southwest' division, selecting...")
            southwestDivision.click()
            Thread.sleep(1000)
        } else {
            println("⚠ Southwest division not found")
        }

        // Scroll down to find player names
        println("📜 Scrolling down to find player names...")
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 2 / 3,
            device.displayWidth / 2,
            device.displayHeight / 3,
            10
        )
        Thread.sleep(800)

        // Select the top three player names in the list
        // We'll look for clickable text elements that are player names
        println("🔍 Selecting top three players...")

        // Get all clickable elements (potential player names)
        // We'll try to find and click the first three distinct player name elements
        for (i in 1..3) {
            println("  → Selecting player $i...")

            // Find clickable elements that might be player names
            // Strategy: find text elements that are clickable and not badges/tabs
            val playerElements = device.findObjects(By.clickable(true))

            if (playerElements.size >= i) {
                // Filter out known UI elements (tabs, badges, etc.) and select data points
                val potentialPlayers = playerElements.filter { element ->
                    val text = element.text
                    text != null &&
                    !text.contains("NBA") &&
                    !text.contains("NFL") &&
                    !text.contains("NHL") &&
                    !text.contains("Division") &&
                    text.length > 3  // Player names are longer than 3 chars
                }

                if (potentialPlayers.size >= i) {
                    val player = potentialPlayers[i - 1]
                    println("  ✓ Clicking player: ${player.text}")
                    player.click()
                    Thread.sleep(500)
                } else {
                    println("  ⚠ Not enough player elements found")
                }
            }
        }

        Thread.sleep(1000)

        // Scroll back up
        println("📜 Scrolling back up...")
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight / 3,
            device.displayWidth / 2,
            device.displayHeight * 2 / 3,
            10
        )
        Thread.sleep(1000)

        // Final pause to show the result
        println("✓ Showing highlighted data points...")
        Thread.sleep(2000)

        println("✓ Demo complete!")
    }

    /**
     * Demo test: Refresh button in header
     * Shows: Opening the app > Tapping refresh button in header
     */
    @Test
    fun testDemo_RefreshButton() {
        println("🎬 RECORDING_READY - Starting demo...")

        println("⏱ Waiting for app to load...")
        Thread.sleep(1500)

        // Look for the refresh button in the header
        println("🔍 Looking for refresh button in header...")
        val refreshButton = device.wait(
            Until.findObject(By.desc("Refresh")),
            5000
        )

        if (refreshButton != null) {
            println("✓ Found refresh button, tapping...")
            refreshButton.click()
            Thread.sleep(1000)

            // Show the refreshed state
            println("✓ Data should be refreshing...")
            Thread.sleep(2000)
        } else {
            println("⚠ Refresh button not found, trying alternative search...")

            // Try to find by icon or clickable element at typical refresh button location
            val refreshAlt = device.wait(
                Until.findObject(By.descContains("refresh").clickable(true)),
                3000
            )

            if (refreshAlt != null) {
                println("✓ Found refresh button (alternative), tapping...")
                refreshAlt.click()
                Thread.sleep(1000)
                println("✓ Data should be refreshing...")
                Thread.sleep(2000)
            } else {
                println("⚠ Refresh button not found by description, trying top-right corner...")
                // Try clicking approximate location of refresh button (top right area)
                device.click(device.displayWidth - 150, 138)
                Thread.sleep(1000)
                println("✓ Attempted to tap refresh button location...")
                Thread.sleep(2000)
            }
        }

        // Final pause to show the refreshed content
        println("✓ Showing refreshed content...")
        Thread.sleep(2000)

        println("✓ Demo complete!")
    }

    /**
     * Demo test: Navigate through charts in the default list
     * Shows: Select each chart > scroll inside > back > next chart
     */
    @Test
    fun testDemo_NavigateChartsAndShowInfo() {
        println("🎬 RECORDING_READY - Starting demo...")

        println("⏱ Waiting for app to load...")
        Thread.sleep(1500)

        val screenHeight = device.displayHeight
        val screenWidth = device.displayWidth

        // Chart keywords to find (case-insensitive partial match)
        val chartKeywords = listOf(
            "matchup",
            "rating",
            "player efficiency",
            "team efficiency"
        )

        for ((index, keyword) in chartKeywords.withIndex()) {
            println("📊 [${index + 1}/${chartKeywords.size}] Looking for '$keyword' chart...")

            // Find chart by keyword (case-insensitive via regex)
            val chart = device.wait(
                Until.findObject(By.text(java.util.regex.Pattern.compile(".*$keyword.*", java.util.regex.Pattern.CASE_INSENSITIVE))),
                3000
            )

            if (chart != null) {
                println("  ✓ Found '$keyword', tapping...")
                chart.click()
                Thread.sleep(1200)

                // Scroll down a bit inside the chart
                println("  → Scrolling in chart...")
                device.swipe(
                    screenWidth / 2,
                    screenHeight * 2 / 3,
                    screenWidth / 2,
                    screenHeight / 3,
                    15
                )
                Thread.sleep(600)

                // Scroll back up
                device.swipe(
                    screenWidth / 2,
                    screenHeight / 3,
                    screenWidth / 2,
                    screenHeight * 2 / 3,
                    15
                )
                Thread.sleep(400)

                // Navigate back to the chart list
                println("  ← Navigating back...")
                val backButton = device.wait(
                    Until.findObject(By.desc("Back")),
                    2000
                )

                if (backButton != null) {
                    backButton.click()
                } else {
                    device.pressBack()
                }
                Thread.sleep(800)
            } else {
                println("  ⚠ '$keyword' chart not found, skipping...")
            }
        }

        // Final pause
        println("✓ Showing final chart list view...")
        Thread.sleep(1500)

        println("✓ Demo complete!")
    }

    /**
     * Demo test: Generic Matchup Worksheet interaction
     * Shows: Opening app > Select Matchups item > Scroll dates > Select date >
     *        Scroll matchups > Select matchup > Scroll stats > View charts > Scroll charts
     */
    @Test
    fun testDemo_MatchupWorksheet() {
        println("🎬 RECORDING_READY - Starting demo...")

        println("⏱ Waiting for app to load...")
        Thread.sleep(1500)

        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight

        // Find and tap on a Matchup Worksheet item (be specific to avoid clicking refresh button)
        println("🔍 Looking for 'Matchup Worksheet' item...")

        // Try to find "Matchup Worksheet" first (most specific) - fast search
        var matchupItem = device.wait(
            Until.findObject(By.textContains("Matchup Worksheet")),
            1500
        )

        // If not found, try "Matchups" (plural - likely a card title)
        if (matchupItem == null) {
            println("  → Trying 'Matchups'...")
            matchupItem = device.wait(
                Until.findObject(By.textContains("Matchups")),
                1000
            )
        }

        // Last resort: find all "Matchup" elements and pick one that's likely a card (not in header)
        if (matchupItem == null) {
            println("  → Looking for any 'Matchup' text below header area...")
            val matchupElements = device.findObjects(By.textContains("Matchup"))
            // Filter to elements that are below the header (y > 200) to avoid refresh/menu buttons
            matchupItem = matchupElements.find { it.visibleBounds.top > 200 }
        }

        if (matchupItem != null) {
            println("✓ Found '${matchupItem.text}', tapping...")
            matchupItem.click()
            Thread.sleep(1000)
        } else {
            println("⚠ No Matchup Worksheet item found")
            return
        }

        println("✓ Matchup Worksheet should be visible")
        Thread.sleep(1000)

        // Scroll through the dates (horizontal scroll)
        // Dates row is below the header - use a lower Y position to avoid header/collapse caret
        println("📅 Scrolling through dates...")
        val datesY = screenHeight / 7  // Lower than header, in the dates area

        // Swipe LEFT to demonstrate date navigation (start from center-right, end center)
        println("  → Swiping dates left to see more...")
        device.swipe(
            screenWidth * 3 / 4,  // Start from right side (but not edge)
            datesY,
            screenWidth / 2,      // End at center
            datesY,
            25
        )
        Thread.sleep(1000)

        // Select a different date by tapping in the dates area
        println("🔍 Selecting a different date...")
        device.click(screenWidth / 2, datesY)
        Thread.sleep(1000)

        // Scroll through the matchups (horizontal scroll below dates)
        println("📱 Scrolling through matchups...")
        val matchupsY = screenHeight / 5  // Matchups row is below dates

        // Swipe LEFT to demonstrate matchup navigation
        println("  → Swiping matchups left to see more...")
        device.swipe(
            screenWidth * 3 / 4,  // Start from right side (but not edge)
            matchupsY,
            screenWidth / 2,      // End at center
            matchupsY,
            25
        )
        Thread.sleep(1000)

        // Select a matchup by tapping (tap on right side where we scrolled to)
        println("🔍 Selecting a matchup...")
        device.click(screenWidth * 2 / 3, matchupsY)
        Thread.sleep(1000)

        println("✓ Matchup selected, stats should be visible")

        // Scroll down through the stats
        println("📜 Scrolling through stats...")
        device.swipe(
            screenWidth / 2,
            screenHeight * 2 / 3,
            screenWidth / 2,
            screenHeight / 3,
            15
        )
        Thread.sleep(1000)

        // Scroll back up
        device.swipe(
            screenWidth / 2,
            screenHeight / 3,
            screenWidth / 2,
            screenHeight * 2 / 3,
            15
        )
        Thread.sleep(1000)

        // Find and tap on "Charts" tab
        println("🔍 Looking for 'Charts' tab...")
        val chartsTab = device.wait(
            Until.findObject(By.textContains("Charts")),
            5000
        )

        if (chartsTab != null) {
            println("✓ Found 'Charts' tab, tapping...")
            chartsTab.click()
            Thread.sleep(1500)
        } else {
            println("⚠ 'Charts' tab not found, trying 'Chart'...")
            val chartAlt = device.wait(
                Until.findObject(By.textContains("Chart")),
                3000
            )
            if (chartAlt != null) {
                chartAlt.click()
                Thread.sleep(1500)
            } else {
                println("⚠ Could not find Charts tab")
            }
        }

        println("✓ Charts view should be visible")
        Thread.sleep(1000)

        // Scroll through the charts
        println("📜 Scrolling through charts...")
        device.swipe(
            screenWidth / 2,
            screenHeight * 2 / 3,
            screenWidth / 2,
            screenHeight / 4,
            20
        )
        Thread.sleep(1500)

        // Select "Last 5 Wks" filter first
        println("🔍 Looking for 'Last 5' filter...")
        val last5Badge = device.wait(
            Until.findObject(By.textContains("Last 5")),
            2000
        )

        if (last5Badge != null) {
            println("✓ Found 'Last 5 Wks', tapping...")
            last5Badge.click()
            Thread.sleep(1500)
        } else {
            println("⚠ 'Last 5' filter not found")
        }

        // Then select "Prior 5 Wks" filter
        println("🔍 Looking for 'Prior 5' filter...")
        val prior5Badge = device.wait(
            Until.findObject(By.textContains("Prior 5")),
            2000
        )

        if (prior5Badge != null) {
            println("✓ Found 'Prior 5 Wks', tapping...")
            prior5Badge.click()
            Thread.sleep(1500)
        } else {
            println("⚠ 'Prior 5' filter not found")
        }

        // Pause to show the filtered chart
        println("✓ Showing filtered chart...")
        Thread.sleep(1500)

        // Scroll back up to show more of the chart
        println("📜 Scrolling back up...")
        device.swipe(
            screenWidth / 2,
            screenHeight / 3,
            screenWidth / 2,
            screenHeight * 2 / 3,
            20
        )
        Thread.sleep(2000)

        println("✓ Demo complete!")
    }

    /**
     * Demo test: Generic Matchup Worksheet share image
     * Shows: Opening app > Select Matchups item > Select matchup > Tap share FAB
     */
    @Test
    fun testDemo_MatchupShareImage() {
        println("🎬 RECORDING_READY - Starting share image demo...")

        println("⏱ Waiting for app to load...")
        Thread.sleep(1500)

        // Find and tap on a Matchup Worksheet item (be specific to avoid clicking refresh button)
        println("🔍 Looking for 'Matchup Worksheet' item...")

        // Try to find "Matchup Worksheet" first (most specific) - fast search
        var matchupItem = device.wait(
            Until.findObject(By.textContains("Matchup Worksheet")),
            1500
        )

        // If not found, try "Matchups" (plural - likely a card title)
        if (matchupItem == null) {
            println("  → Trying 'Matchups'...")
            matchupItem = device.wait(
                Until.findObject(By.textContains("Matchups")),
                1000
            )
        }

        // Last resort: find all "Matchup" elements and pick one that's likely a card (not in header)
        if (matchupItem == null) {
            println("  → Looking for any 'Matchup' text below header area...")
            val matchupElements = device.findObjects(By.textContains("Matchup"))
            // Filter to elements that are below the header (y > 200) to avoid refresh/menu buttons
            matchupItem = matchupElements.find { it.visibleBounds.top > 200 }
        }

        if (matchupItem != null) {
            println("✓ Found '${matchupItem.text}', tapping...")
            matchupItem.click()
            Thread.sleep(1500)
        } else {
            println("⚠ No Matchup Worksheet item found")
            return
        }

        println("✓ Matchup Worksheet should be visible")
        Thread.sleep(1000)

        // Pause to show the worksheet content
        println("📊 Showing matchup worksheet content...")
        Thread.sleep(1500)

        // Find and tap the Share FAB (Floating Action Button)
        println("🔍 Looking for Share FAB button...")
        val shareFab = device.wait(
            Until.findObject(By.desc("Share")),
            5000
        )

        if (shareFab != null) {
            println("✓ Found Share FAB, tapping...")
            shareFab.click()
            Thread.sleep(2000)
        } else {
            println("⚠ Share FAB not found by description, trying alternative search...")
            // Try to find any FAB-like element (floating action buttons are typically clickable)
            val fabAlt = device.wait(
                Until.findObject(By.descContains("share").clickable(true)),
                3000
            )

            if (fabAlt != null) {
                println("✓ Found Share button (alternative), tapping...")
                fabAlt.click()
                Thread.sleep(2000)
            } else {
                println("⚠ Share button not found, trying bottom-right corner (typical FAB location)...")
                // Try clicking the typical FAB location (bottom-right corner)
                device.click(
                    device.displayWidth - 150,
                    device.displayHeight - 200
                )
                Thread.sleep(2000)
            }
        }

        println("✓ Share dialog should be visible")
        Thread.sleep(2000)

        // Final pause to show the share dialog
        println("✓ Showing share dialog...")
        Thread.sleep(2000)

        println("✓ Demo complete!")
    }

    /**
     * Demo test: Topics screen walkthrough
     * Shows: Navigate to Topics > Scroll through topic > Select data point > Back >
     *        Scroll more > Mark as read > Scroll more
     */
    @Test
    fun testDemo_TopicsWalkthrough() {
        println("🎬 RECORDING_READY - Starting topics walkthrough demo...")

        println("⏱ Waiting for app to load...")
        Thread.sleep(1500)

        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight

        // 1) Navigate to Topics screen
        println("🔍 Looking for 'Topics' navigation item...")
        var topicsNav = device.wait(
            Until.findObject(By.textContains("Topics")),
            3000
        )

        if (topicsNav == null) {
            // Try finding it in bottom navigation or side menu
            println("  → Trying to find Topics in navigation...")
            topicsNav = device.wait(
                Until.findObject(By.descContains("Topics")),
                2000
            )
        }

        if (topicsNav != null) {
            println("✓ Found 'Topics', tapping...")
            topicsNav.click()
            Thread.sleep(1500)
        } else {
            println("⚠ Topics navigation not found")
            return
        }

        println("✓ Topics screen should be visible")
        Thread.sleep(1500)

        // 2) Scroll down once to reveal content
        println("📜 Scrolling down through topics...")
        device.swipe(
            screenWidth / 2,
            screenHeight * 2 / 3,
            screenWidth / 2,
            screenHeight / 3,
            20
        )
        Thread.sleep(1500)

        // 3) Look for the "data points" section and select a data point
        println("🔍 Looking for 'data points' section...")
        val dataPointsSection = device.wait(
            Until.findObject(By.text("data points")),
            3000
        )

        if (dataPointsSection != null) {
            println("✓ Found 'data points' section")
            Thread.sleep(500)

            // The data points are listed below the badge - find one with an arrow (clickable)
            println("🔍 Looking for clickable data point with arrow...")
            val arrowElement = device.wait(
                Until.findObject(By.text("→")),
                2000
            )

            if (arrowElement != null) {
                // Click on the same row as the arrow (to the left of it)
                println("✓ Found clickable data point, tapping...")
                val bounds = arrowElement.visibleBounds
                device.click(screenWidth / 2, bounds.centerY())
                Thread.sleep(2000)

                // 4) We should now be on a chart/detail screen - show it
                println("📊 Showing linked chart content...")
                Thread.sleep(2000)

                // Press back to return to topics
                println("🔙 Pressing back to return to topics...")
                device.pressBack()
                Thread.sleep(1500)
            } else {
                // No arrow found, try clicking below the data points badge
                println("  → No arrow found, tapping below data points section...")
                val bounds = dataPointsSection.visibleBounds
                device.click(screenWidth / 2, bounds.bottom + 30)
                Thread.sleep(2000)

                println("📊 Showing linked chart content...")
                Thread.sleep(2000)

                println("🔙 Pressing back to return to topics...")
                device.pressBack()
                Thread.sleep(1500)
            }
        } else {
            println("⚠ 'data points' section not found, trying to scroll more...")
            // Scroll down more to find data points
            device.swipe(
                screenWidth / 2,
                screenHeight * 2 / 3,
                screenWidth / 2,
                screenHeight / 3,
                20
            )
            Thread.sleep(1000)

            // Try again
            val dataPointsRetry = device.wait(
                Until.findObject(By.text("data points")),
                2000
            )

            if (dataPointsRetry != null) {
                println("✓ Found 'data points' section on retry")
                val bounds = dataPointsRetry.visibleBounds
                device.click(screenWidth / 2, bounds.bottom + 30)
                Thread.sleep(2000)

                println("📊 Showing linked chart content...")
                Thread.sleep(2000)

                println("🔙 Pressing back to return to topics...")
                device.pressBack()
                Thread.sleep(1500)
            } else {
                println("⚠ Still no data points section found, continuing...")
            }
        }

        // 5) Scroll more through topics
        println("📜 Scrolling more through topics...")
        device.swipe(
            screenWidth / 2,
            screenHeight * 2 / 3,
            screenWidth / 2,
            screenHeight / 3,
            20
        )
        Thread.sleep(1000)

        // 6) Select "Mark as read" button (text is "[mark as read]" with brackets)
        println("🔍 Looking for '[mark as read]' button...")
        var markAsRead = device.wait(
            Until.findObject(By.text("[mark as read]")),
            2000
        )

        if (markAsRead == null) {
            // Try partial match
            markAsRead = device.wait(
                Until.findObject(By.textContains("mark as read")),
                1000
            )
        }

        if (markAsRead != null) {
            println("✓ Found 'mark as read', tapping...")
            markAsRead.click()
            Thread.sleep(1500)
        } else {
            println("⚠ 'mark as read' not found, continuing...")
        }

        // 7) Scroll more to show additional content
        println("📜 Final scroll through topics...")
        device.swipe(
            screenWidth / 2,
            screenHeight * 2 / 3,
            screenWidth / 2,
            screenHeight / 3,
            20
        )
        Thread.sleep(1000)

        // Scroll back up to show more content
        device.swipe(
            screenWidth / 2,
            screenHeight / 3,
            screenWidth / 2,
            screenHeight * 2 / 3,
            20
        )
        Thread.sleep(1500)

        println("✓ Demo complete!")
    }
}
