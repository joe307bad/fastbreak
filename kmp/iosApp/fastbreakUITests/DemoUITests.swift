import XCTest

/// UI Tests for recording automated app demos
/// This test class contains a single demo: Pan and Zoom on NFL Team Tiers chart
class DemoUITests: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["UI_TESTING"]
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    // MARK: - Helper Methods for Pinch Gestures

    /// Performs a pinch-out (zoom in) gesture on an element at a specific vertical position
    func performPinchOut(on element: XCUIElement, atNormalizedY y: CGFloat) {
        // Start with fingers close together, move them apart
        let center = element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: y))
        let leftStart = element.coordinate(withNormalizedOffset: CGVector(dx: 0.45, dy: y))
        let leftEnd = element.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: y))
        let rightStart = element.coordinate(withNormalizedOffset: CGVector(dx: 0.55, dy: y))
        let rightEnd = element.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: y))

        // Simulate two fingers moving apart
        leftStart.press(forDuration: 0.05, thenDragTo: leftEnd)
        rightStart.press(forDuration: 0.05, thenDragTo: rightEnd)
    }

    /// Performs a pinch-in (zoom out) gesture on an element at a specific vertical position
    func performPinchIn(on element: XCUIElement, atNormalizedY y: CGFloat) {
        // Start with fingers far apart, move them together
        let leftStart = element.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: y))
        let leftEnd = element.coordinate(withNormalizedOffset: CGVector(dx: 0.45, dy: y))
        let rightStart = element.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: y))
        let rightEnd = element.coordinate(withNormalizedOffset: CGVector(dx: 0.55, dy: y))

        // Simulate two fingers moving together
        leftStart.press(forDuration: 0.05, thenDragTo: leftEnd)
        rightStart.press(forDuration: 0.05, thenDragTo: rightEnd)
    }

    // MARK: - Demo: Pinch to Zoom and Pan on NFL Team Tiers

    func testDemo_PinchToZoomAndPan() throws {
        // Wait for app to load (reduced wait time)
        print("⏱ Waiting for app to load...")
        sleep(UInt32(1))

        // Find and tap on "NFL Team Tiers" list item
        print("🔍 Looking for 'NFL Team Tiers' text...")
        let teamTiersText = app.staticTexts.containing(NSPredicate(format: "label CONTAINS[c] 'NFL Team Tier'")).firstMatch

        // Wait for the element to appear (charts need time to load)
        if teamTiersText.waitForExistence(timeout: 10) {
            print("✓ Found 'NFL Team Tiers', tapping...")
            teamTiersText.tap()
            sleep(UInt32(1))
        } else {
            print("⚠ 'NFL Team Tiers' not found, trying to find any element with 'Team Tiers'...")
            let anyTeamTiers = app.staticTexts.containing(NSPredicate(format: "label CONTAINS[c] 'Team Tiers'")).firstMatch
            if anyTeamTiers.waitForExistence(timeout: 5) {
                anyTeamTiers.tap()
                sleep(UInt32(2))
            } else {
                print("⚠ Could not find Team Tiers text, continuing anyway...")
                sleep(UInt32(2))
            }
        }

        // Find the chart element
        print("🔍 Looking for chart element with testTag 'chart'...")
        var chartElement: XCUIElement?

        // Try by accessibility identifier first (testTag becomes accessibilityIdentifier on iOS)
        let chartById = app.otherElements["chart"].firstMatch
        if chartById.waitForExistence(timeout: 5) {
            chartElement = chartById
            print("✓ Found chart by testTag identifier 'chart'")
        } else {
            print("⚠ Chart with testTag not found, trying fallback methods...")

            // Try to find any scrollable view or chart-like element
            let scrollViews = app.scrollViews
            if scrollViews.count > 0 {
                chartElement = scrollViews.firstMatch
                print("✓ Found chart via scrollView")
            } else {
                // Try to find by other elements
                let otherElements = app.otherElements.element(boundBy: 0)
                if otherElements.exists {
                    chartElement = otherElements
                    print("✓ Using first otherElement as chart")
                }
            }
        }

        // If we found a chart element, perform pan and zoom
        if let chart = chartElement, chart.exists {
            print("✓ Chart found, starting pan and zoom demo...")

            // Initial pause to show the chart
            sleep(UInt32(1))

            // Zoom in - manual two-finger pinch out gesture on upper chart area
            print("  → Zooming in with pinch out gesture...")
            performPinchOut(on: chart, atNormalizedY: 0.25)
            sleep(UInt32(2))

            // Pan around while zoomed
            print("  → Panning left...")
            let panStart = chart.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.25))
            let panEnd = chart.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.25))
            panStart.press(forDuration: 0.1, thenDragTo: panEnd)
            sleep(UInt32(1))

            // Pan right
            print("  → Panning right...")
            let panRight = chart.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.25))
            let panRightEnd = chart.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.25))
            panRight.press(forDuration: 0.1, thenDragTo: panRightEnd)
            sleep(UInt32(1))

            // Zoom out - manual two-finger pinch in gesture
            print("  → Zooming out with pinch in gesture...")
            performPinchIn(on: chart, atNormalizedY: 0.25)
            sleep(UInt32(2))

            // Final zoom in
            print("  → Final zoom in...")
            performPinchOut(on: chart, atNormalizedY: 0.25)
            sleep(UInt32(2))

        } else {
            print("⚠ Chart element not found")
            // Even if we can't find the chart, let's try panning/zooming on the main view
            let mainView = app.otherElements.element(boundBy: 0)
            if mainView.exists {
                print("⚠ Attempting pan/zoom on main view instead...")
                mainView.pinch(withScale: 2.0, velocity: 1.0)
                sleep(UInt32(1))
                mainView.swipeLeft()
                sleep(UInt32(1))
                mainView.swipeRight()
                sleep(UInt32(1))
            }
        }

        // Final pause before ending
        print("✓ Demo complete!")
        sleep(UInt32(1))
    }
}

// MARK: - XCUIElement Extensions

extension XCUIElement {
    func swipeLeft(velocity: XCUIGestureVelocity = .default) {
        let start = self.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5))
        let end = self.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.5))
        start.press(forDuration: 0.1, thenDragTo: end, withVelocity: velocity, thenHoldForDuration: 0)
    }

    func swipeRight(velocity: XCUIGestureVelocity = .default) {
        let start = self.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.5))
        let end = self.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5))
        start.press(forDuration: 0.1, thenDragTo: end, withVelocity: velocity, thenHoldForDuration: 0)
    }

    func swipeUp(velocity: XCUIGestureVelocity = .default) {
        let start = self.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9))
        let end = self.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1))
        start.press(forDuration: 0.1, thenDragTo: end, withVelocity: velocity, thenHoldForDuration: 0)
    }

    func swipeDown(velocity: XCUIGestureVelocity = .default) {
        let start = self.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1))
        let end = self.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9))
        start.press(forDuration: 0.1, thenDragTo: end, withVelocity: velocity, thenHoldForDuration: 0)
    }
}
