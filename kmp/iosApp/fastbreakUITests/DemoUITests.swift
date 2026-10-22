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

    /// Where the mock API listens. The simulator shares the host's loopback,
    /// so this reaches the WireMock the capture script starts.
    private static let mockBaseUrl = "http://127.0.0.1:8089"

    /// Launch with a chosen look. Every screenshot picks its own so the set on
    /// the listing shows the app in light, in dark, and in a few team palettes
    /// rather than eleven shots of one theme.
    private func launch(theme: String, teamTheme: String = "none") throws {
        app = XCUIApplication()
        setupSnapshot(app)
        app.launchArguments += [
            "UI_TESTING",
            "-fastbreakBaseUrl", DemoUITests.mockBaseUrl,
            "-fastbreakTheme", theme,
            "-fastbreakTeamTheme", teamTheme,
        ]
        app.launch()
        try waitForChartList()
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
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

    /// No screenshot may contain a loading state, so every flow waits for the
    /// chart list to settle before it does anything. Against the local mock
    /// that takes moments; if it never happens the run should fail rather than
    /// photograph a spinner.
    private func waitForChartList() throws {
        let deadline = Date().addingTimeInterval(90)
        while Date() < deadline {
            if chartListIsSettled() { return }
            if exactly("no charts available").exists || exactly("Retry").exists {
                tapIfPresent(described("Refresh"), timeout: 2, "Refresh")
                tapIfPresent(exactly("Retry"), timeout: 2, "Retry")
            }
            usleep(500_000)
        }
        XCTFail("Chart list never settled - a screenshot here would show a loading state")
    }

    /// Nothing on screen is still loading.
    private func waitUntilLoaded(timeout: TimeInterval = 20) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let loading = containing("Syncing charts").exists
                || containing("loading").exists
                || containing("Loading").exists
                || app.activityIndicators.firstMatch.exists
            if !loading { return }
            usleep(500_000)
        }
        print("⚠ something still looks like it is loading")
    }

    /// A chart list with cached rows and no sync banner over them.
    private func chartListIsSettled() -> Bool {
        let syncing = containing("Syncing charts").exists
            || containing("loading registry").exists
        let cached = containing(" ago").exists || containing("just now").exists
        return cached && !syncing
    }

    /// Back out to the chart list between flows.
    private func returnToChartList() {
        for _ in 0..<3 {
            if chartListIsSettled() { return }
            if !tapIfPresent(described("Back"), timeout: 3, "Back") { break }
            settle()
        }
    }

    /// Switch to a league tab. The app opens on NFL, which carries no
    /// efficiency chart and no bracket, so a flow that wants one has to say
    /// which league it is looking in.
    private func openSport(_ code: String) {
        if tapIfPresent(exactly(code), timeout: 8, "\(code) tab") {
            settle(2)
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

    @discardableResult
    private func openFirstChart(matching keyword: String, _ what: String) -> Bool {
        let matches = allContaining(keyword)
        guard let target = matches.first(where: { $0.isHittable }) ?? matches.first else {
            print("⚠ no chart matching \(keyword)")
            return false
        }
        return tapIfPresent(target, timeout: 8, what)
    }

    /// Open a chart or abandon the flow. Skipping leaves a visible gap in the
    /// run instead of a screenshot of the chart list standing in for the
    /// feature it was meant to show.
    private func openChartOrSkip(
        sport: String,
        matching keyword: String,
        _ what: String
    ) throws {
        openSport(sport)
        guard openFirstChart(matching: keyword, what) else {
            throw XCTSkip("\(what) not present on the \(sport) tab")
        }
        waitUntilLoaded()
    }

    // MARK: - Flows
    //
    // Ten screenshots, which is Apple's per-device maximum. Every one runs in
    // its own team palette so the listing reads as colourful rather than ten
    // shots of the same purple, and the palette matches whatever league is on
    // screen. The chart behind each is chosen from a payload that actually has
    // data: NBA player efficiency and every playoff bracket are empty at this
    // point in the calendar and would photograph an empty grid.

    func testScreenshot_01_Home() throws {
        try launch(theme: "light", teamTheme: "nfl:KC")
        snapshot("01-Home")
    }

    func testScreenshot_02_ScoringLeaders() throws {
        try launch(theme: "light", teamTheme: "nhl:PIT")
        try openChartOrSkip(sport: "NHL", matching: "Scoring Leaders", "scoring leaders")
        settle(2)
        snapshot("02-ScoringLeaders")
    }

    func testScreenshot_03_HighlightDataPoints() throws {
        try launch(theme: "light", teamTheme: "mlb:LAD")
        try openChartOrSkip(sport: "MLB", matching: "Hitting Leaders", "hitting leaders")

        // Tapping a team badge filters the plot to that team.
        tapIfPresent(exactly("LAD"), timeout: 6, "LAD badge")
        settle(2)
        snapshot("03-HighlightDataPoints")
    }

    func testScreenshot_04_DivisionFilter() throws {
        try launch(theme: "dark", teamTheme: "nhl:BOS")
        try openChartOrSkip(sport: "NHL", matching: "Team Efficiency", "team efficiency")

        tapIfPresent(containing("Division"), timeout: 6, "division filter")
        settle()
        tapIfPresent(containing("Metropolitan"), timeout: 4, "Metropolitan")
        settle(2)
        snapshot("04-DivisionFilter")
    }

    func testScreenshot_05_ChartZoom() throws {
        try launch(theme: "dark", teamTheme: "nfl:PHI")
        try openChartOrSkip(sport: "NFL", matching: "Team Tier List", "team tier list")

        let chart = chartCanvas()
        if chart.exists {
            pinchOut(chart, atNormalizedY: 0.25)
            settle(2)
            drag(chart, from: CGVector(dx: 0.65, dy: 0.25), to: CGVector(dx: 0.45, dy: 0.25))
            settle()
        }
        waitUntilLoaded()
        snapshot("05-ChartZoom")
    }

    func testScreenshot_06_MatchupWorksheet() throws {
        try launch(theme: "light", teamTheme: "nfl:SF")
        try openChartOrSkip(sport: "NFL", matching: "Matchups", "NFL matchups")
        settle(2)
        snapshot("06-MatchupWorksheet")
    }

    func testScreenshot_07_ReportCard() throws {
        try launch(theme: "dark", teamTheme: "nba:GSW")
        try openReportCard()
        snapshot("07-ReportCard")
    }

    /// The expandable percentile rankings: tapping a player's stat opens a
    /// bottom sheet ranking every qualified player in that group league-wide.
    func testScreenshot_08_RankingSheet() throws {
        try launch(theme: "light", teamTheme: "mlb:BOS")
        try openReportCard()

        guard openRankingSheet() else {
            throw XCTSkip("no tappable player stat found on the report card")
        }
        waitUntilLoaded()
        settle()
        snapshot("08-RankingSheet")
    }

    func testScreenshot_09_ManageTeams() throws {
        try launch(theme: "dark", teamTheme: "nhl:VGK")
        guard tapIfPresent(described("Menu"), timeout: 8, "menu") else {
            throw XCTSkip("drawer menu not reachable")
        }
        settle()
        tapIfPresent(containing("settings"), timeout: 6, "settings")
        settle()
        tapIfPresent(containing("manage teams"), timeout: 6, "manage teams")
        waitUntilLoaded()
        settle()
        snapshot("09-ManageTeams")
    }

    func testScreenshot_10_Topics() throws {
        try launch(theme: "light", teamTheme: "nba:BOS")

        // Topics sits behind the header's list icon, with the drawer as the
        // fallback route.
        if !tapIfPresent(described("Topics"), timeout: 6, "topics icon") {
            tapIfPresent(described("Menu"), timeout: 4, "menu")
            settle()
            guard tapIfPresent(containing("Topics"), timeout: 5, "topics via menu") else {
                throw XCTSkip("topics not reachable")
            }
        }
        waitUntilLoaded()
        settle(2)
        snapshot("10-Topics")
    }

    // MARK: - Shared steps

    private func openReportCard() throws {
        try openChartOrSkip(sport: "NFL", matching: "Report Card", "report card")
        let ready = exactly("Quarterbacks").waitForExistence(timeout: 20)
            || containing("Composite").waitForExistence(timeout: 5)
        print(ready ? "✓ report card rendered" : "⚠ report card never rendered")
        waitUntilLoaded()
        settle()
    }

    /// The sheet titles itself "Player Rankings" or "Season Rankings". Checked
    /// for hittability rather than existence: XCUITest reports an element as
    /// existing while it is still off screen, which made an earlier version
    /// believe a sheet had opened when nothing had.
    private func rankingSheetIsOpen() -> Bool {
        let title = app.descendants(matching: .any)
            .matching(NSPredicate(
                format: "label ==[c] 'Player Rankings' OR label ==[c] 'Season Rankings'"
            ))
            .firstMatch
        return title.exists && title.isHittable
    }

    /// Tap a stat cell to raise the league-wide ranking sheet. Every cell in a
    /// player row is clickable, so this walks the numeric cells until one of
    /// them actually produces a sheet.
    @discardableResult
    private func openRankingSheet() -> Bool {
        let numeric = NSPredicate(format: "label MATCHES %@", "^-?[0-9]+\\.[0-9]{1,2}$")

        for attempt in 0..<3 {
            // Work down the card: the first screen is team rows, the player
            // tables sit under them.
            if attempt > 0 {
                app.swipeUp()
                settle()
            }

            let candidates = app.staticTexts.matching(numeric).allElementsBoundByIndex
            for candidate in candidates.prefix(12) where candidate.isHittable {
                candidate.tap()
                settle(2)
                if rankingSheetIsOpen() {
                    print("✓ ranking sheet open")
                    return true
                }
            }
        }

        print("⚠ no stat cell opened a ranking sheet")
        return false
    }
}
