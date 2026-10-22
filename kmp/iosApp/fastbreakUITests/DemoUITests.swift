import XCTest

/// App Store screenshot capture, ported from the Android UI Automator demos in
/// androidApp/src/androidTest/kotlin/com/joebad/fastbreak/DemoUITests.kt so both
/// platforms walk the same ten flows.
///
/// Each flow drives the app to one interesting state and calls `snapshot(...)`.
/// The names are numbered because fastlane's deliver uploads a device's
/// screenshots in alphabetical order, and that order is what the App Store
/// listing shows.
///
/// Run through fastlane rather than Xcode:
///   cd kmp && ./scripts/capture-ios-screenshots.sh
///
/// Main-actor isolated because fastlane's `setupSnapshot` and `snapshot` are:
/// they drive XCUIApplication, which is main-actor only.
@MainActor
final class DemoUITests: XCTestCase {

    var app: XCUIApplication!

    /// Charts stream in from the CDN on first launch, so the first flow to run
    /// waits for them. Static so the wait is paid once per simulator, not once
    /// per flow.
    private static var chartsSynced = false

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        setupSnapshot(app)
        app.launchArguments += ["UI_TESTING"]
        app.launch()
        try waitForChartsOnce()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    // MARK: - Element lookup
    //
    // The Android flows select on visible text (By.text / By.textContains) and
    // on content descriptions (By.desc). These are the XCUITest equivalents.

    /// Anything whose label is exactly `text`, case-insensitively.
    private func exactly(_ text: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label ==[c] %@", text))
            .firstMatch
    }

    /// Anything whose label contains `text`, case-insensitively.
    private func containing(_ text: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", text))
            .firstMatch
    }

    private func allContaining(_ text: String) -> [XCUIElement] {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", text))
            .allElementsBoundByIndex
    }

    /// Compose's `Modifier.testTag` and a content description both surface as an
    /// accessibility identifier on iOS, which is what By.desc matched on Android.
    private func described(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier ==[c] %@ OR label ==[c] %@", identifier, identifier))
            .firstMatch
    }

    @discardableResult
    private func tapIfPresent(_ element: XCUIElement, timeout: TimeInterval = 8, _ what: String) -> Bool {
        guard element.waitForExistence(timeout: timeout) else {
            print("⚠ not found: \(what)")
            return false
        }
        // A hit-testable element takes a tap; anything else is still tappable
        // through its centre coordinate, which is how Compose canvases behave.
        if element.isHittable {
            element.tap()
        } else {
            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        print("✓ tapped: \(what)")
        return true
    }

    private func settle(_ seconds: UInt32 = 1) {
        sleep(seconds)
    }

    // MARK: - Chart sync

    /// Mirrors testHelper_SyncChartsForDemo: the app is useless for screenshots
    /// until the registry has been pulled and at least one chart is cached.
    private func waitForChartsOnce() throws {
        guard !DemoUITests.chartsSynced else { return }

        print("⏱ waiting for charts to cache...")
        let deadline = Date().addingTimeInterval(240)

        while Date() < deadline {
            if containing(" ago").exists || containing("just now").exists {
                print("✓ cached charts visible")
                DemoUITests.chartsSynced = true
                return
            }
            if exactly("no charts available").exists || exactly("Retry").exists {
                print("  → empty list, tapping refresh")
                tapIfPresent(described("Refresh"), timeout: 3, "Refresh")
                tapIfPresent(exactly("Retry"), timeout: 3, "Retry")
            }
            sleep(5)
        }

        // Not fatal: a flow may still have something worth capturing, and
        // failing setUp would lose every later flow too.
        print("⚠ charts never reported as cached; capturing anyway")
        DemoUITests.chartsSynced = true
    }

    /// Back out to the chart list between flows.
    private func returnToChartList() {
        for _ in 0..<3 {
            if containing(" ago").exists || containing("just now").exists { return }
            if !tapIfPresent(described("Back"), timeout: 3, "Back") { break }
            settle()
        }
    }

    // MARK: - Gestures

    private func pinchOut(_ element: XCUIElement, atNormalizedY y: CGFloat) {
        let leftStart = element.coordinate(withNormalizedOffset: CGVector(dx: 0.45, dy: y))
        let leftEnd = element.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: y))
        let rightStart = element.coordinate(withNormalizedOffset: CGVector(dx: 0.55, dy: y))
        let rightEnd = element.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: y))
        leftStart.press(forDuration: 0.05, thenDragTo: leftEnd)
        rightStart.press(forDuration: 0.05, thenDragTo: rightEnd)
    }

    private func drag(_ element: XCUIElement, from: CGVector, to: CGVector) {
        element.coordinate(withNormalizedOffset: from)
            .press(forDuration: 0.1, thenDragTo: element.coordinate(withNormalizedOffset: to))
    }

    /// The chart canvas. Compose draws it as one element, so it is found by tag
    /// and otherwise by falling back to the largest scrollable thing on screen.
    private func chartCanvas() -> XCUIElement {
        let tagged = app.otherElements["chart"].firstMatch
        if tagged.waitForExistence(timeout: 5) { return tagged }
        if app.scrollViews.firstMatch.exists { return app.scrollViews.firstMatch }
        return app.otherElements.element(boundBy: 0)
    }

    private func openFirstChart(matching keyword: String, _ what: String) -> Bool {
        let matches = allContaining(keyword)
        guard let target = matches.first(where: { $0.isHittable }) ?? matches.first else {
            print("⚠ no chart matching \(keyword)")
            return false
        }
        return tapIfPresent(target, timeout: 8, what)
    }

    // MARK: - 01 Home

    func testScreenshot_01_Home() throws {
        settle(2)
        snapshot("01-Home")
    }

    // MARK: - 02 Pinch to zoom and pan (Android: testDemo_PinchToZoomAndPan)

    func testScreenshot_02_ChartZoom() throws {
        guard openFirstChart(matching: "Efficiency", "efficiency chart") else {
            snapshot("02-ChartZoom")
            return
        }
        settle(2)

        let chart = chartCanvas()
        if chart.exists {
            pinchOut(chart, atNormalizedY: 0.25)
            settle(2)
            drag(chart, from: CGVector(dx: 0.65, dy: 0.25), to: CGVector(dx: 0.45, dy: 0.25))
            settle()
        }
        snapshot("02-ChartZoom")
        returnToChartList()
    }

    // MARK: - 03 Highlighting data points (Android: testDemo_HighlightingDataPoints)

    func testScreenshot_03_HighlightDataPoints() throws {
        guard openFirstChart(matching: "Player Efficiency", "player efficiency chart") else {
            snapshot("03-HighlightDataPoints")
            return
        }
        settle(2)

        // Tapping a team badge filters the plot to that team.
        tapIfPresent(exactly("PHI"), timeout: 6, "PHI badge")
        settle()
        tapIfPresent(exactly("LAL"), timeout: 4, "LAL badge")
        settle(2)

        snapshot("03-HighlightDataPoints")
        returnToChartList()
    }

    // MARK: - 04 Division filter (Android: testDemo_HighlightingDataPoints, second half)

    func testScreenshot_04_DivisionFilter() throws {
        guard openFirstChart(matching: "Efficiency", "efficiency chart") else {
            snapshot("04-DivisionFilter")
            return
        }
        settle(2)

        tapIfPresent(containing("Division"), timeout: 6, "division filter")
        settle()
        tapIfPresent(containing("Southwest"), timeout: 4, "Southwest")
        settle(2)

        snapshot("04-DivisionFilter")
        returnToChartList()
    }

    // MARK: - 05 Manage teams (Android: testDemo_ManageTeamsAndFilter)

    func testScreenshot_05_ManageTeams() throws {
        settle()
        guard tapIfPresent(described("Menu"), timeout: 8, "menu") else {
            snapshot("05-ManageTeams")
            return
        }
        settle()
        tapIfPresent(containing("settings"), timeout: 6, "settings")
        settle()
        tapIfPresent(containing("manage teams"), timeout: 6, "manage teams")
        settle(2)

        snapshot("05-ManageTeams")

        tapIfPresent(described("Back"), timeout: 4, "back")
        returnToChartList()
    }

    // MARK: - 06 Chart info sheet (Android: testDemo_NavigateChartsAndShowInfo)

    func testScreenshot_06_ChartInfo() throws {
        guard openFirstChart(matching: "Efficiency", "efficiency chart") else {
            snapshot("06-ChartInfo")
            return
        }
        settle(2)

        // The info control carries an "Info"/"About" description depending on
        // the screen; try both before giving up on the sheet.
        if !tapIfPresent(described("Info"), timeout: 5, "info") {
            tapIfPresent(described("About"), timeout: 3, "about")
        }
        settle(2)

        snapshot("06-ChartInfo")
        returnToChartList()
    }

    // MARK: - 07 Matchup worksheet (Android: testDemo_MatchupWorksheet)

    func testScreenshot_07_MatchupWorksheet() throws {
        var opened = openFirstChart(matching: "Matchup Worksheet", "matchup worksheet")
        if !opened { opened = openFirstChart(matching: "Matchups", "matchups") }
        guard opened else {
            snapshot("07-MatchupWorksheet")
            return
        }
        settle(3)

        snapshot("07-MatchupWorksheet")
        returnToChartList()
    }

    // MARK: - 08 Matchup charts tab (Android: testDemo_MatchupWorksheet, charts tab)

    func testScreenshot_08_MatchupCharts() throws {
        var opened = openFirstChart(matching: "Matchup Worksheet", "matchup worksheet")
        if !opened { opened = openFirstChart(matching: "Matchups", "matchups") }
        guard opened else {
            snapshot("08-MatchupCharts")
            return
        }
        settle(2)

        tapIfPresent(containing("Charts"), timeout: 6, "charts tab")
        settle(3)

        snapshot("08-MatchupCharts")
        returnToChartList()
    }

    // MARK: - 09 Topics (Android: testDemo_TopicsWalkthrough)

    func testScreenshot_09_Topics() throws {
        settle()
        if !tapIfPresent(containing("Topics"), timeout: 8, "topics") {
            tapIfPresent(described("Menu"), timeout: 4, "menu")
            settle()
            tapIfPresent(containing("Topics"), timeout: 4, "topics via menu")
        }
        settle(3)

        snapshot("09-Topics")
        returnToChartList()
    }

    // MARK: - 10 Report card (Android: testDemo_MLBReportCardWalkthrough)

    func testScreenshot_10_ReportCard() throws {
        guard openFirstChart(matching: "Report Card", "report card") else {
            snapshot("10-ReportCard")
            return
        }

        // The card is only worth capturing once a category has rendered.
        let ready = exactly("Hitters").waitForExistence(timeout: 20)
            || exactly("Forwards").waitForExistence(timeout: 5)
            || exactly("Guards").waitForExistence(timeout: 5)
            || containing("Composite").waitForExistence(timeout: 5)
        print(ready ? "✓ report card rendered" : "⚠ report card never rendered")
        settle(2)

        snapshot("10-ReportCard")
        returnToChartList()
    }

    // MARK: - 11 Playoff bracket (Android: testDemo_BracketInteraction)

    func testScreenshot_11_Bracket() throws {
        guard openFirstChart(matching: "Bracket", "bracket") else {
            snapshot("11-Bracket")
            return
        }
        settle(3)

        snapshot("11-Bracket")
        returnToChartList()
    }
}
