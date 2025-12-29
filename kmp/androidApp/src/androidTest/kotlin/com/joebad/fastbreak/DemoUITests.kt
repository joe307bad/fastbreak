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
}
