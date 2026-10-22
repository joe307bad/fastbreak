import SwiftUI
import Sentry
import shared

@main
struct iOSApp: App {
    init() {
        // A UI test can redirect the API host and pin the theme. Read before
        // anything composes or fetches; a normal launch passes no such
        // arguments and nothing changes.
        UiTestOverrides.shared.applyLaunchArguments(
            arguments: ProcessInfo.processInfo.arguments
        )

        SentrySDK.start { options in
            options.dsn = "https://9b034d4f24117a7ef9d9f233cc88e7c7@o170588.ingest.us.sentry.io/4510546022105088"
            options.tracesSampleRate = 1.0
            options.enableAutoSessionTracking = true
            #if DEBUG
            options.debug = true
            options.environment = "development"
            #else
            options.environment = "production"
            #endif
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
