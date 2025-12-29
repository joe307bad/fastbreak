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

    // MARK: - Demo: Pinch to Zoom and Pan on NFL Team Tiers

    func testDemo_PinchToZoomAndPan() throws {
        // Wait for app to load
        print("⏱ Waiting for app to load...")
        sleep(3)

        // Find and tap on "NFL Team Tiers" list item
        print("🔍 Looking for 'NFL Team Tiers' text...")
        let teamTiersText = app.staticTexts.containing(NSPredicate(format: "label CONTAINS[c] 'NFL Team Tier'")).firstMatch

        // Wait for the element to appear (charts need time to load)
        if teamTiersText.waitForExistence(timeout: 10) {
            print("✓ Found 'NFL Team Tiers', tapping...")
            teamTiersText.tap()
            sleep(2)
        } else {
            print("⚠ 'NFL Team Tiers' not found, trying to find any element with 'Team Tiers'...")
            let anyTeamTiers = app.staticTexts.containing(NSPredicate(format: "label CONTAINS[c] 'Team Tiers'")).firstMatch
            if anyTeamTiers.waitForExistence(timeout: 5) {
                anyTeamTiers.tap()
                sleep(2)
            } else {
                print("⚠ Could not find Team Tiers text, continuing anyway...")
                sleep(2)
            }
        }

        // Find the chart element
        print("🔍 Looking for chart element...")
        // Try multiple ways to find the chart
        var chartElement: XCUIElement?

        // Try by accessibility identifier first
        let chartById = app.otherElements["chart"].firstMatch
        if chartById.exists {
            chartElement = chartById
            print("✓ Found chart by identifier 'chart'")
        }

        // Try to find any scrollable view or chart-like element
        if chartElement == nil {
            let scrollViews = app.scrollViews
            if scrollViews.count > 0 {
                chartElement = scrollViews.firstMatch
                print("✓ Found chart via scrollView")
            }
        }

        // Try to find by other elements
        if chartElement == nil {
            let otherElements = app.otherElements.element(boundBy: 0)
            if otherElements.exists {
                chartElement = otherElements
                print("✓ Using first otherElement as chart")
            }
        }

        // If we found a chart element, perform pan and zoom
        if let chart = chartElement, chart.exists {
            print("✓ Chart found, starting pan and zoom demo...")

            // Initial pause to show the chart
            sleep(1)

            // Zoom in (pinch out)
            print("  → Pinching to zoom in...")
            chart.pinch(withScale: 2.0, velocity: 1.0)
            sleep(1.5)

            // Pan left
            print("  → Panning left...")
            chart.swipeLeft(velocity: .slow)
            sleep(1)

            // Pan right
            print("  → Panning right...")
            chart.swipeRight(velocity: .slow)
            sleep(1)

            // Pan up
            print("  → Panning up...")
            chart.swipeUp(velocity: .slow)
            sleep(1)

            // Pan down
            print("  → Panning down...")
            chart.swipeDown(velocity: .slow)
            sleep(1)

            // Zoom out (pinch in)
            print("  → Pinching to zoom out...")
            chart.pinch(withScale: 0.5, velocity: 1.0)
            sleep(1.5)

            // Final zoom in to show it works
            print("  → Final zoom in...")
            chart.pinch(withScale: 1.8, velocity: 1.0)
            sleep(1)

        } else {
            print("⚠ Chart element not found")
            // Even if we can't find the chart, let's try panning/zooming on the main view
            let mainView = app.otherElements.element(boundBy: 0)
            if mainView.exists {
                print("⚠ Attempting pan/zoom on main view instead...")
                mainView.pinch(withScale: 2.0, velocity: 1.0)
                sleep(1)
                mainView.swipeLeft()
                sleep(1)
                mainView.swipeRight()
                sleep(1)
            }
        }

        // Final pause before ending
        print("✓ Demo complete!")
        sleep(2)
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
