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
     * Demo test: Pinch to zoom and pan on NFL Team Tiers chart
     */
    @Test
    fun testDemo_PinchToZoomAndPan() {
        println("⏱ Waiting for app to load...")
        Thread.sleep(2000)

        // Find and tap on "NFL Team Tiers" text using UiObject2
        println("🔍 Looking for 'NFL Team Tiers' text...")
        val teamTiersText = device.wait(
            Until.findObject(By.textContains("NFL Team Tier")),
            10000
        )

        if (teamTiersText != null) {
            println("✓ Found 'NFL Team Tiers', tapping...")
            teamTiersText.click()
            Thread.sleep(1000)
        } else {
            println("⚠ 'NFL Team Tiers' not found, trying alternative search...")
            val alternativeText = device.wait(
                Until.findObject(By.textContains("Team Tiers")),
                5000
            )
            if (alternativeText != null) {
                alternativeText.click()
                Thread.sleep(2000)
            } else {
                println("⚠ Could not find Team Tiers text, continuing anyway...")
                Thread.sleep(2000)
            }
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

        // Select Pittsburgh Penguins from results
        println("🔍 Looking for Pittsburgh Penguins in results...")
        val penguinsResult = device.wait(
            Until.findObject(By.textContains("Pittsburgh Penguins")),
            3000
        ) ?: device.wait(
            Until.findObject(By.textContains("Penguins")),
            2000
        )

        if (penguinsResult != null) {
            println("✓ Found Penguins, selecting...")
            penguinsResult.click()
            Thread.sleep(500)
        } else {
            println("⚠ Pittsburgh Penguins not found in results")
        }

        // Close the team selector bottom sheet first
        println("🔍 Closing team selector bottom sheet...")
        device.pressBack()
        Thread.sleep(500)

        // Now navigate back from Settings to home screen
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

        // Find and tap "Scoring Leaders" chart
        println("🔍 Looking for 'Scoring Leaders' chart...")
        val scoringLeadersChart = device.wait(
            Until.findObject(By.textContains("Scoring Leader")),
            5000
        )

        if (scoringLeadersChart != null) {
            println("✓ Found 'Scoring Leaders', tapping...")
            scoringLeadersChart.click()
            Thread.sleep(1000)
        } else {
            println("⚠ Scoring Leaders chart not found")
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
     * Shows: NBA tab > Select PHI and LAL badges > Division badge > Southwest Division > Select top 3 players
     */
    @Test
    fun testDemo_HighlightingDataPoints() {
        println("🎬 RECORDING_READY - Starting demo...")

        println("⏱ Waiting for app to load...")
        Thread.sleep(1500)

        // Switch to NBA tab
        println("🔍 Looking for NBA tab...")
        val nbaTab = device.wait(
            Until.findObject(By.text("NBA")),
            3000
        )

        if (nbaTab != null) {
            println("✓ Found NBA tab, tapping...")
            nbaTab.click()
            Thread.sleep(1000)
        } else {
            println("⚠ NBA tab not found")
        }

        // Find and tap "Player Efficiency" chart
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
     * Demo test: Navigate through all charts and show info bottom sheet
     * Shows: Cumulative EPA > Back > Team Snaps > Back > Playoff Odds > Back > Turnover Differential > Info button
     */
    @Test
    fun testDemo_NavigateChartsAndShowInfo() {
        println("🎬 RECORDING_READY - Starting demo...")

        println("⏱ Waiting for app to load...")
        Thread.sleep(1500)

        // Define the charts to visit in order
        val chartNames = listOf(
            "Cumulative EPA",
            "Team Snaps",
            "Playoff Odds",
            "Turnover Differential"
        )

        for ((index, chartName) in chartNames.withIndex()) {
            val isLastChart = index == chartNames.size - 1
            println("📊 Looking for '$chartName' chart...")

            // Find the chart by name
            val chart = device.wait(
                Until.findObject(By.textContains(chartName)),
                5000
            )

            if (chart != null) {
                println("  ✓ Found '$chartName', tapping...")
                chart.click()
                Thread.sleep(1000)

                // If this is the last chart (Turnover Differential), click the info button
                if (isLastChart) {
                    println("  🔍 Looking for info button on '$chartName'...")
                    val infoButton = device.wait(
                        Until.findObject(By.desc("Chart Info")),
                        3000
                    )

                    if (infoButton != null) {
                        println("  ✓ Found info button, tapping...")
                        infoButton.click()
                        Thread.sleep(1500)

                        // Show the bottom sheet for a moment
                        println("  ✓ Bottom sheet description should be visible")
                        Thread.sleep(2000)

                        // Close the bottom sheet
                        println("  → Closing bottom sheet...")
                        device.pressBack()
                        Thread.sleep(500)
                    } else {
                        println("  ⚠ Info button not found, trying to click top-right area...")
                        // Try clicking the approximate location of info button (top right)
                        device.click(device.displayWidth - 100, 200)
                        Thread.sleep(1500)

                        // If bottom sheet opened, close it
                        device.pressBack()
                        Thread.sleep(500)
                    }
                }

                // Navigate back to the chart list
                println("  ← Navigating back to chart list...")
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
                println("  ⚠ '$chartName' chart not found, skipping...")
            }
        }

        // Final pause
        println("✓ Showing final chart list view...")
        Thread.sleep(1500)

        println("✓ Demo complete!")
    }
}
